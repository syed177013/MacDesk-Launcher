package com.example.macdesk

import kotlinx.coroutines.flow.*

enum class AppKind(val displayName: String) {
    FINDER("Finder"), PREFERENCES("System Preferences"), ABOUT("About This Tablet"), IMAGE_EDITOR("Preview"), PLAYER("Player")
}

data class Bounds(val x: Float, val y: Float, val w: Float, val h: Float)

/** All geometry in dp. */
data class WindowState(
    val id: Int,
    val app: AppKind,
    val title: String,
    val arg: String? = null,
    val positionX: Float,
    val positionY: Float,
    val width: Float,
    val height: Float,
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val zIndex: Int,
    val restoreBounds: Bounds? = null
)

/** Usable window area in dp. [maxHeight] excludes the dock so maximized windows don't hide it. */
data class Workspace(val width: Float, val height: Float, val maxHeight: Float)

fun List<WindowState>.focused(): WindowState? = filter { !it.isMinimized }.maxByOrNull { it.zIndex }

private fun clampSafe(v: Float, lo: Float, hi: Float) = if (hi < lo) lo else v.coerceIn(lo, hi)

class WindowManager {
    companion object {
        const val MIN_W = 320f
        const val MIN_H = 220f
        private const val GRAB = 96f   // keep at least this much of the header on-screen
    }

    private val _windows = MutableStateFlow<List<WindowState>>(emptyList())
    val windows: StateFlow<List<WindowState>> = _windows.asStateFlow()
    private var nextId = 1
    private var topZ = 0

    fun open(
        app: AppKind, title: String = app.displayName, arg: String? = null,
        ws: Workspace, compact: Boolean, forceNew: Boolean = false
    ): Int {
        if (!forceNew) _windows.value.firstOrNull { it.app == app }?.let { focus(it.id); return it.id }
        val (dw, dh) = when (app) {
            AppKind.FINDER -> 720f to 460f
            AppKind.PREFERENCES -> 580f to 460f
            AppKind.ABOUT -> 340f to 300f
            AppKind.IMAGE_EDITOR -> 820f to 560f
            AppKind.PLAYER -> 800f to 500f
        }
        val w = clampSafe(dw, MIN_W.coerceAtMost(ws.width), ws.width - 24f)
        val h = clampSafe(dh, MIN_H.coerceAtMost(ws.maxHeight), ws.maxHeight - 24f)
        val n = _windows.value.size % 8
        val x = clampSafe(48f + n * 30f, 0f, ws.width - w)
        val y = clampSafe(24f + n * 30f, 0f, ws.maxHeight - h)
        val win = WindowState(
            id = nextId++, app = app, title = title, arg = arg,
            positionX = x, positionY = y, width = w, height = h,
            isMaximized = compact, zIndex = ++topZ,
            restoreBounds = if (compact) Bounds(x, y, w, h) else null
        )
        _windows.update { it + win }
        return win.id
    }

    /** Bring to front (and un-minimize). */
    fun focus(id: Int) = _windows.update { list ->
        val w = list.firstOrNull { it.id == id } ?: return@update list
        if (!w.isMinimized && w.zIndex == topZ) return@update list
        list.map { if (it.id == id) it.copy(isMinimized = false, zIndex = ++topZ) else it }
    }

    fun close(id: Int) = _windows.update { l -> l.filterNot { it.id == id } }
    fun minimize(id: Int) = mutate(id) { it.copy(isMinimized = true) }

    fun toggleMaximize(id: Int) = mutate(id) { w ->
        if (w.isMaximized) {
            w.restoreBounds?.let {
                w.copy(isMaximized = false, positionX = it.x, positionY = it.y, width = it.w, height = it.h)
            } ?: w.copy(isMaximized = false)
        } else w.copy(
            isMaximized = true,
            restoreBounds = Bounds(w.positionX, w.positionY, w.width, w.height)
        )
    }

    fun move(id: Int, dx: Float, dy: Float, ws: Workspace) = mutate(id) {
        if (it.isMaximized) it else it.copy(
            positionX = clampSafe(it.positionX + dx, GRAB - it.width, ws.width - GRAB),
            positionY = clampSafe(it.positionY + dy, 0f, ws.maxHeight - 40f)
        )
    }

    fun resize(id: Int, dx: Float, dy: Float, ws: Workspace) = mutate(id) {
        if (it.isMaximized) it else it.copy(
            width = clampSafe(it.width + dx, MIN_W, ws.width - it.positionX),
            height = clampSafe(it.height + dy, MIN_H, ws.maxHeight - it.positionY)
        )
    }

    fun tile(id: Int, ws: Workspace, right: Boolean) = mutate(id) {
        it.copy(isMaximized = false, positionX = if (right) ws.width / 2f else 0f, positionY = 0f, width = ws.width / 2f, height = ws.maxHeight)
    }

    /** Tile to the left/right half when a window is dragged mostly past a screen edge. */
    fun snap(id: Int, ws: Workspace) = mutate(id) { w ->
        fun tile(x: Float) = w.copy(positionX = x, positionY = 0f, width = ws.width / 2f, height = ws.maxHeight)
        when {
            w.isMaximized -> w
            w.positionX < -w.width * 0.30f -> tile(0f)
            w.positionX + w.width > ws.width + w.width * 0.30f -> tile(ws.width / 2f)
            else -> w
        }
    }

    /** Re-fit floating windows after rotation / fold / split-screen changes. */
    fun clampAll(ws: Workspace) = _windows.update { list ->
        list.map {
            if (it.isMaximized) it else {
                val w = clampSafe(it.width, MIN_W.coerceAtMost(ws.width), ws.width)
                val h = clampSafe(it.height, MIN_H.coerceAtMost(ws.maxHeight), ws.maxHeight)
                it.copy(
                    width = w, height = h,
                    positionX = clampSafe(it.positionX, GRAB - w, ws.width - GRAB),
                    positionY = clampSafe(it.positionY, 0f, (ws.maxHeight - 40f).coerceAtLeast(0f))
                )
            }
        }
    }

    private fun mutate(id: Int, f: (WindowState) -> WindowState) =
        _windows.update { l -> l.map { if (it.id == id) f(it) else it } }
}
