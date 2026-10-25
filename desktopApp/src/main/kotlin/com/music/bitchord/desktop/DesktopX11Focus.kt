package com.music.bitchord.desktop

import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.Toolkit
import java.awt.Window
import javax.swing.SwingUtilities

/**
 * Takes the keyboard when the window is handed X input focus the way Java does not listen for.
 *
 * Java's X11 windows ask to be told to take the focus (ICCCM's WM_TAKE_FOCUS) and then move it to
 * an invisible child of their own, which is where the keys are read. xwayland-satellite, which runs
 * X11 windows under niri and other Wayland compositors, instead sets the X focus on the window
 * itself; Java ignores that, so after switching workspaces the window looked focused but heard no
 * keys until it was clicked (xwayland-satellite issue #372). This watches the window from a
 * connection of its own and, when the focus lands on it like that, asks for the focus the way a
 * click does. A window manager that sends WM_TAKE_FOCUS never puts the focus there, so this stays
 * idle under one.
 */
internal object DesktopX11Focus {

    private const val FOCUS_IN = 9
    private const val NOTIFY_ANCESTOR = 0
    private const val NOTIFY_INFERIOR = 2
    private const val NOTIFY_NONLINEAR = 3
    private const val POLL_MS = 100L

    // Focus-in details that put the focus on the window itself; the virtual ones mean it is on a
    // child, where Java keeps it.
    private val DIRECT = setOf(NOTIFY_ANCESTOR, NOTIFY_INFERIOR, NOTIFY_NONLINEAR)

    @Volatile
    private var installed = false

    fun install(window: Window) {
        if (installed || !DesktopPlatform.isLinux) return
        // Only Java's own X11 toolkit has the problem, and only under Xwayland.
        if (Toolkit.getDefaultToolkit().javaClass.name != "sun.awt.X11.XToolkit") return
        if (System.getenv("WAYLAND_DISPLAY").isNullOrBlank()) return
        installed = true
        Thread({ runCatching { watch(window) } }, "x11-focus").apply { isDaemon = true }.start()
    }

    private fun watch(window: Window) {
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return
        try {
            // The top-level X window is the one the focus is put on, the window's own drawable sits
            // inside it.
            val shell = topLevel(x11, display, X11.Window(windowId(window))) ?: return
            x11.XSelectInput(display, shell, NativeLong(X11.FocusChangeMask.toLong()))
            val event = X11.XEvent()
            while (true) {
                var focusedDirectly = false
                while (x11.XPending(display) > 0) {
                    x11.XNextEvent(display, event)
                    if (event.type != FOCUS_IN) continue
                    event.setType(X11.XFocusChangeEvent::class.java)
                    event.read()
                    focusedDirectly = event.xfocus.detail in DIRECT
                }
                if (focusedDirectly) {
                    SwingUtilities.invokeLater {
                        if (!window.isFocused) (window.mostRecentFocusOwner ?: window).requestFocus()
                    }
                }
                Thread.sleep(POLL_MS)
            }
        } finally {
            x11.XCloseDisplay(display)
        }
    }

    private fun windowId(window: Window): Long {
        var id = 0L
        while (id == 0L) {
            if (window.isDisplayable) {
                SwingUtilities.invokeAndWait { id = runCatching { Native.getWindowID(window) }.getOrDefault(0L) }
            }
            if (id == 0L) Thread.sleep(500)
        }
        return id
    }

    /** The ancestor of [window] that is a child of the root, which the window manager focuses. */
    private fun topLevel(x11: X11, display: X11.Display, window: X11.Window): X11.Window? {
        var current = window
        repeat(16) {
            val root = X11.WindowByReference()
            val parent = X11.WindowByReference()
            val children = PointerByReference()
            if (x11.XQueryTree(display, current, root, parent, children, IntByReference()) == 0) return null
            children.value?.let { x11.XFree(it) }
            if (parent.value == null || parent.value == root.value) return current
            current = parent.value
        }
        return null
    }
}
