# Wear OS (not built in this fork)

Upstream's Wear OS watch app is not part of this fork's build: `:wearos` is not
included in `settings.gradle.kts`, so the phone app ships without a bundled
watch APK and its Wear OS settings row stays hidden.

Only `protocol/` is kept, because the phone app compiles the shared
phone/watch protocol from it (see `app/build.gradle.kts`).
