package com.music.bitchord.desktop

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Interactive desktop sign-in for Chromium-family browsers.
 *
 * Current Chrome protects account cookies with App-Bound Encryption, deliberately preventing a
 * different executable from decrypting the main profile. Google also rejects account login while
 * remote debugging is active. We therefore sign in normally in a separate browser profile first;
 * after that window closes, the same profile is opened headlessly with loopback debugging only
 * long enough for Chrome to hand the completed session back to BitChord.
 */
internal object DesktopBrowserSignIn {
    data class Browser(val name: String, val executable: Path) {
        val label: String get() = "Sign in with $name"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Installed Chromium-family browsers; keep every choice visible, including Brave. */
    fun installed(): List<Browser> {
        if (!DesktopPlatform.isWindows) {
            val path = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator)
            return onPath(path.filter(String::isNotBlank).map(Paths::get))
        }
        val local = environmentPath("LOCALAPPDATA", "AppData/Local")
        val roots = listOfNotNull(local, System.getenv("ProgramFiles")?.let(Paths::get),
            System.getenv("ProgramFiles(x86)")?.let(Paths::get))
        return listOf(
            "Chrome" to "Google/Chrome/Application/chrome.exe",
            "Edge" to "Microsoft/Edge/Application/msedge.exe",
            "Brave" to "BraveSoftware/Brave-Browser/Application/brave.exe",
            "Vivaldi" to "Vivaldi/Application/vivaldi.exe",
            "Chromium" to "Chromium/Application/chrome.exe",
        ).mapNotNull { (name, relative) -> roots.firstNotNullOfOrNull { candidate(name, it.resolve(relative)) } }
    }

    internal fun onPath(directories: List<Path>): List<Browser> = listOf(
        "Chromium" to listOf("chromium", "chromium-browser"),
        "Brave" to listOf("brave-browser", "brave-browser-stable", "brave"),
        "Chrome" to listOf("google-chrome", "google-chrome-stable"),
        "Edge" to listOf("microsoft-edge", "microsoft-edge-stable"),
        "Vivaldi" to listOf("vivaldi", "vivaldi-stable"),
    ).mapNotNull { (name, commands) ->
        commands.asSequence().flatMap { command -> directories.asSequence().map { it.resolve(command) } }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
            ?.let { Browser(name, it) }
    }

    fun preferred(): Browser? = installed().firstOrNull()

    enum class Service(val url: String, val domain: String) {
        YOUTUBE("https://music.youtube.com/", "youtube.com"),
        SPOTIFY("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F", "spotify.com"),
    }

    /** Opens [browser] normally for sign-in, then reads the finished session in a headless pass. */
    suspend fun capture(browser: Browser, service: Service = Service.YOUTUBE): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val profile = profile(browser, service)
        Files.createDirectories(profile)
        val portFile = profile.resolve(DEVTOOLS_ACTIVE_PORT)
        Files.deleteIfExists(portFile)

        var signInProcess: Process? = launch(
            browser,
            profile,
            "--disable-background-mode",
            "--no-first-run",
            "--no-default-browser-check",
            "--new-window",
            service.url,
        )
        var captureProcess: Process? = null
        var cdp: DevTools? = null
        try {
            // Google sees an ordinary Chrome launch here. The close is the listener's explicit
            // signal that login is finished and the cookie database has been flushed to disk.
            while (signInProcess?.isAlive == true) delay(BROWSER_CLOSE_POLL_MS)
            signInProcess = null
            Files.deleteIfExists(portFile)

            val readerProcess = launch(
                browser,
                profile,
                "--headless=new",
                "--disable-background-mode",
                "--remote-debugging-port=0",
                "--remote-debugging-address=127.0.0.1",
                "about:blank",
            )
            captureProcess = readerProcess
            val endpoint = waitForEndpoint(portFile, readerProcess)
            cdp = DevTools(endpoint)
            repeat(CAPTURE_POLLS) {
                val header = cdp.cookieHeader(service.domain)
                if (service == Service.SPOTIFY) {
                    header.split("; ").firstOrNull { it.startsWith("sp_dc=") }
                        ?.substringAfter("=")?.takeIf { it.isNotBlank() }?.let { return@withContext it }
                } else if (DesktopBrowserCookies.hasSigningSecret(header)) return@withContext header
                delay(POLL_INTERVAL_MS)
            }
            error("${browser.name} did not contain a signed-in ${service.name.lowercase()} session. Finish signing in, then close its sign-in window.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            cdp?.closeBrowser()
            signInProcess?.stop()
            captureProcess?.stop()
        }
    }

    private fun profile(browser: Browser, service: Service): Path =
        (if (DesktopPlatform.isWindows) environmentPath("LOCALAPPDATA", "AppData/Local")
         else Paths.get(System.getProperty("user.home"), ".local", "share"))
            .resolve("BitChord").resolve("Browser Sign In").resolve(browser.name)
            .let { if (service == Service.SPOTIFY) it.resolve(service.name) else it }

    fun clearSpotifyProfiles() {
        listOf("Chromium", "Brave", "Chrome", "Edge", "Vivaldi").forEach { name ->
            profile(Browser(name, Paths.get(".")), Service.SPOTIFY).toFile().deleteRecursively()
        }
    }

    /** Let the real web player mint its token, with its own current request signing. */
    suspend fun spotifyToken(browser: Browser): JsonObject = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val directory = profile(browser, Service.SPOTIFY)
        Files.createDirectories(directory)
        val portFile = directory.resolve(DEVTOOLS_ACTIVE_PORT)
        Files.deleteIfExists(portFile)
        val process = launch(browser, directory, "--headless=new", "--disable-background-mode",
            "--remote-debugging-port=0", "--remote-debugging-address=127.0.0.1", "about:blank")
        var cdp: DevTools? = null
        try {
            cdp = DevTools(waitForEndpoint(portFile, process))
            val target = cdp.command("Target.createTarget", buildJsonObject { put("url", "about:blank") })["result"]!!.jsonObject["targetId"]!!.jsonPrimitive.content
            val session = cdp.command("Target.attachToTarget", buildJsonObject {
                put("targetId", target); put("flatten", true)
            })["result"]!!.jsonObject["sessionId"]!!.jsonPrimitive.content
            cdp.command("Page.enable", sessionId = session)
            // Clear cached web-player tokens while keeping the login cookies.
            cdp.command("Storage.clearDataForOrigin", buildJsonObject {
                put("origin", "https://open.spotify.com"); put("storageTypes", "local_storage,indexeddb,cache_storage,service_workers")
            }, session)
            cdp.command("Page.addScriptToEvaluateOnNewDocument", buildJsonObject {
                put("source", SPOTIFY_TOKEN_HOOK)
            }, session)
            cdp.command("Page.navigate", buildJsonObject { put("url", "https://open.spotify.com/") }, session)
            repeat(60) {
                val result = cdp.command("Runtime.evaluate", buildJsonObject {
                    put("expression", "JSON.stringify(window.__bitchordSpotifyToken || null)")
                    put("returnByValue", true)
                }, session)["result"]?.jsonObject?.get("result")?.jsonObject?.get("value")?.jsonPrimitive?.content
                val token = result?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
                if (token != null && token["isAnonymous"]?.jsonPrimitive?.content != "true" &&
                    !token["accessToken"]?.jsonPrimitive?.content.isNullOrBlank()) return@withContext token
                delay(500)
            }
            error("Spotify could not refresh this account. Sign in again from Settings.")
        } finally {
            cdp?.closeBrowser()
            process.stop()
        }
    }

    private val SPOTIFY_TOKEN_HOOK = """
        (() => {
          const report = text => { try {
            const body = JSON.parse(text);
            if (body.accessToken && !body.isAnonymous) window.__bitchordSpotifyToken = body;
          } catch (_) {} };
          const originalFetch = window.fetch;
          window.fetch = function(...args) {
            return originalFetch.apply(this, args).then(response => {
              if (response.url.includes('/api/token')) response.clone().text().then(report).catch(() => {});
              return response;
            });
          };
          const open = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function(method, url, ...rest) {
            if (String(url).includes('/api/token')) this.addEventListener('load', () => report(this.responseText));
            return open.call(this, method, url, ...rest);
          };
        })();
    """.trimIndent()

    private fun launch(browser: Browser, profile: Path, vararg arguments: String): Process =
        ProcessBuilder(
            browser.executable.toString(),
            "--user-data-dir=${profile.toAbsolutePath()}",
            *arguments,
        )
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

    private fun Process.stop() {
        descendants().forEach { child -> runCatching { child.destroy() } }
        runCatching {
            destroy()
            if (!waitFor(3, TimeUnit.SECONDS)) destroyForcibly()
        }
    }

    private suspend fun waitForEndpoint(portFile: Path, process: Process): URI {
        repeat(STARTUP_POLLS) {
            val lines = runCatching { Files.readAllLines(portFile) }.getOrNull()
            if (lines != null && lines.size >= 2) {
                val port = lines[0].trim().toIntOrNull()
                val path = lines[1].trim()
                if (port != null && path.startsWith("/")) return URI("ws://127.0.0.1:$port$path")
            }
            if (!process.isAlive) error("The browser closed before the account session could be read.")
            delay(STARTUP_POLL_MS)
        }
        error("Could not connect to ${portFile.parent.fileName}. Close its other sign-in window and try again.")
    }

    private fun candidate(name: String, path: Path): Browser? =
        path.takeIf(Files::isRegularFile)?.let { Browser(name, it) }

    private fun environmentPath(variable: String, fallback: String): Path =
        System.getenv(variable)?.takeIf(String::isNotBlank)?.let(Paths::get)
            ?: Paths.get(System.getProperty("user.home")).resolve(fallback)

    /** A minimal request/response client for the browser-level Chrome DevTools Protocol socket. */
    private class DevTools(endpoint: URI) : WebSocket.Listener {
        private val sequence = AtomicInteger()
        private val waiting = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
        private val text = StringBuilder()
        private val socket: WebSocket = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(endpoint, this)
            .get(10, TimeUnit.SECONDS)

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) {
                val message = runCatching { json.parseToJsonElement(text.toString()).jsonObject }.getOrNull()
                text.setLength(0)
                val id = message?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
                if (id != null) waiting.remove(id)?.complete(message)
            }
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            waiting.values.forEach { it.completeExceptionally(error) }
            waiting.clear()
        }

        fun command(method: String, params: JsonObject? = null, sessionId: String? = null): JsonObject {
            val id = sequence.incrementAndGet()
            val response = CompletableFuture<JsonObject>()
            waiting[id] = response
            val request = buildJsonObject {
                put("id", id)
                put("method", method)
                params?.let { put("params", it) }
                sessionId?.let { put("sessionId", it) }
            }
            try {
                socket.sendText(request.toString(), true).get(10, TimeUnit.SECONDS)
                val message = response.get(10, TimeUnit.SECONDS)
                message["error"]?.let { error(it.toString()) }
                return message
            } finally {
                waiting.remove(id)
            }
        }

        fun cookieHeader(serviceDomain: String): String {
            val cookies = command("Storage.getCookies")["result"]
                ?.jsonObject
                ?.get("cookies")
                ?.jsonArray
                .orEmpty()
            return cookieHeader(cookies, serviceDomain)
        }

        fun closeBrowser() {
            val request = buildJsonObject {
                put("id", sequence.incrementAndGet())
                put("method", "Browser.close")
            }
            runCatching { socket.sendText(request.toString(), true).get(2, TimeUnit.SECONDS) }
            runCatching { socket.abort() }
        }
    }

    internal fun cookieHeader(cookies: List<kotlinx.serialization.json.JsonElement>, serviceDomain: String): String {
        val jar = LinkedHashMap<String, String>()
        cookies.forEach { element ->
            val cookie = element.jsonObject
            val domain = cookie["domain"]?.jsonPrimitive?.content.orEmpty().removePrefix(".")
            if (domain != serviceDomain && !domain.endsWith(".$serviceDomain")) return@forEach
            val name = cookie["name"]?.jsonPrimitive?.content.orEmpty()
            val value = cookie["value"]?.jsonPrimitive?.content.orEmpty()
            if (name.isNotBlank() && value.isNotBlank()) jar[name] = value
        }
        return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private const val DEVTOOLS_ACTIVE_PORT = "DevToolsActivePort"
    private const val STARTUP_POLLS = 200
    private const val STARTUP_POLL_MS = 100L
    private const val BROWSER_CLOSE_POLL_MS = 250L
    private const val CAPTURE_POLLS = 15
    private const val POLL_INTERVAL_MS = 1_000L
}
