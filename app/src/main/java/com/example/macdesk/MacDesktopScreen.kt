package com.example.macdesk

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.content.pm.PackageManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val BUILTIN_CANDIDATES = mapOf(
    AppKind.FINDER.name to listOf("com.google.android.documentsui", "com.android.documentsui", "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files", "com.mi.android.globalFileexplorer", "com.android.fileexplorer"),
    AppKind.PREFERENCES.name to listOf("com.android.settings"),
    AppKind.IMAGE_EDITOR.name to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.android.gallery3d", "com.miui.gallery", "com.google.android.apps.photosgo"),
    AppKind.PLAYER.name to listOf("com.google.android.videos", "com.sec.android.app.videoplayer", "com.miui.videoplayer", "org.videolan.vlc")
)

private const val TOP_LAYER = 1_000_000_000f

private fun isDefaultHome(ctx: android.content.Context): Boolean {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
}

/** Launch bounds (px) for opening an app as a freeform window, cascaded. */
private fun windowBounds(root: IntSize, d: Float, topPx: Float, menuScale: Float, n: Int): android.graphics.Rect {
    val menu = topPx + 30 * menuScale * d
    val availW = root.width.toFloat()
    val availH = root.height - menu - 110 * d
    val w = minOf(availW * 0.7f, 960 * d); val h = minOf(availH * 0.9f, 680 * d)
    val x = (availW - w) / 2 + n * 28 * d; val y = menu + 12 * d + n * 28 * d
    return android.graphics.Rect(x.toInt(), y.toInt(), (x + w).toInt().coerceAtMost(root.width), (y + h).toInt().coerceAtMost(root.height))
}

@Composable
fun MacDesktopScreen(vm: DesktopViewModel, widthClass: WindowWidthSizeClass) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val windows by vm.windows.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val packs by vm.iconPacks.collectAsStateWithLifecycle()
    val launchpad by vm.launchpadVisible.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val notifs by NotificationHub.items.collectAsStateWithLifecycle()
    var showHint by remember { mutableStateOf(false) }
    val dark = settings.darkMode ?: isSystemInDarkTheme()
    val compact = widthClass == WindowWidthSizeClass.Compact

    val wp by produceState<WallpaperSet?>(null, settings.wallpaperUri, dark, settings.loaded) {
        if (settings.loaded) value = loadWallpaperSet(ctx, settings.wallpaperUri?.let { Uri.parse(it) }, dark)
    }
    val wallpaperContent: @Composable () -> Unit = remember(wp) { { WallpaperLayer(wp?.full) } }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val d = LocalDensity.current.density
    val topPx = with(LocalDensity.current) { insets.calculateTopPadding().toPx() }
    val counter = remember { intArrayOf(0) }
    val launchApp: (AppInfo) -> Unit = { app ->
        if (settings.openInWindows && settings.loaded && !settings.windowHintDone && !WindowMode.ready(ctx)) showHint = true
        val bounds = if (settings.openInWindows && rootSize.width > 0) windowBounds(rootSize, d, topPx, settings.menuBarScale, counter[0]++ % 6) else null
        vm.launch(app, bounds)
    }
    var isDefault by remember { mutableStateOf(isDefaultHome(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = isDefaultHome(ctx) }
    // Built-in dock icons follow the active icon pack by borrowing the matching system app's themed icon.
    val builtinIcons = remember(apps, settings.iconPack) {
        if (settings.iconPack == null) emptyMap() else BUILTIN_CANDIDATES.mapNotNull { (k, pkgs) ->
            pkgs.firstNotNullOfOrNull { p -> apps.firstOrNull { it.packageName == p } }?.let { k to it.icon }
        }.toMap()
    }
    val pinnedSet = remember(settings.pinnedPackages) { settings.pinnedPackages.toSet() }
    val deskSet = remember(settings.desktopApps) { settings.desktopApps.toSet() }

    MacTheme(dark) {
        CompositionLocalProvider(
            LocalRootSize provides rootSize, LocalWallpaperContent provides wallpaperContent, LocalBackdrop provides wp?.blurred,
            LocalBuiltinIcons provides builtinIcons
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootSize = it }) {
                Desktop(vm, settings, windows, apps, packs, running, notifs, dark, compact, launchApp)
                AnimatedVisibility(launchpad, enter = fadeIn(), exit = fadeOut()) {
                    LaunchpadOverlay(
                        apps, pinnedSet, deskSet, insets.calculateTopPadding(), insets.calculateBottomPadding(),
                        onLaunch = launchApp, onTogglePin = { vm.togglePin(it) }, onToggleDesktop = { vm.toggleDesktop(it) }, onDismiss = { vm.setLaunchpad(false) }
                    )
                }
                if (showHint) {
                    AlertDialog(
                        onDismissRequest = { showHint = false; vm.setWindowHintDone() },
                        title = { Text("Window mode is off") },
                        text = { Text("Android needs “freeform windows” enabled to show apps as floating windows. Until then, apps open full-screen. It takes a minute to set up.") },
                        confirmButton = { TextButton({ showHint = false; vm.setWindowHintDone(); vm.openPrefs.value = true }) { Text("Set Up") } },
                        dismissButton = { TextButton({ showHint = false; vm.setWindowHintDone() }) { Text("Not Now") } }
                    )
                } else if (settings.loaded && !settings.onboardingDone && !isDefault) {
                    AlertDialog(
                        onDismissRequest = { vm.setOnboardingDone() },
                        title = { Text("Make MacDesk your home screen?") },
                        text = { Text("Set MacDesk as the default home app so the Home button brings you to your desktop.") },
                        confirmButton = {
                            TextButton({ vm.setOnboardingDone(); ctx.safeStart(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }) { Text("Set as Home") }
                        },
                        dismissButton = { TextButton({ vm.setOnboardingDone() }) { Text("Later") } }
                    )
                }
            }
        }
    }
}

@Composable
private fun Desktop(
    vm: DesktopViewModel, settings: DesktopSettings, windows: List<WindowState>,
    apps: List<AppInfo>, packs: List<IconPackInfo>, running: List<String>, notifs: List<NotifItem>, dark: Boolean, compact: Boolean,
    launchApp: (AppInfo) -> Unit
) {
    val ctx = LocalContext.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()
    val menuBarH = topInset + 30.dp * settings.menuBarScale
    val iconSize = (if (compact) 44.dp else 56.dp) * settings.dockScale
    val dockReserve = iconSize + 20.dp + bottomInset + 24.dp
    val wm = vm.windowManager
    val focused = windows.focused()
    val wallpaper = LocalWallpaperContent.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ws = Workspace(maxWidth.value, (maxHeight - menuBarH).value, (maxHeight - menuBarH - dockReserve).value)
        LaunchedEffect(ws) { wm.clampAll(ws) }

        fun openApp(kind: AppKind, arg: String? = null, forceNew: Boolean = false, title: String = kind.displayName) {
            wm.open(kind, title, arg, ws, compact, forceNew)
        }

        // 1) Wallpaper
        wallpaper()

        val openImage: (String, String) -> Unit = { u, n -> openApp(AppKind.IMAGE_EDITOR, u, forceNew = true, title = n) }
        val openMedia: (String, String) -> Unit = { u, n -> openApp(AppKind.PLAYER, u, forceNew = true, title = n) }

        fun appEntry(app: AppInfo, isRunning: Boolean, prefix: String): DockEntry {
            val pinned = app.packageName in settings.pinnedPackages
            val onDesk = app.packageName in settings.desktopApps
            return DockEntry(
                prefix + app.packageName, app.label, isRunning,
                menu = buildList {
                    add(MenuEntry(if (isRunning) "Show" else "Open") { launchApp(app) })
                    if (isRunning) add(MenuEntry("Quit") { vm.quit(app.packageName) })
                    add(MenuEntry(if (pinned) "Remove from Dock" else "Keep in Dock") { vm.togglePin(app.packageName) })
                    if (pinned && prefix == "pkg:") {
                        add(MenuEntry("Move Left") { vm.movePin(app.packageName, -1) })
                        add(MenuEntry("Move Right") { vm.movePin(app.packageName, 1) })
                    }
                    add(MenuEntry(if (onDesk) "Remove from Desktop" else "Add to Desktop") { vm.toggleDesktop(app.packageName) })
                },
                onClick = { launchApp(app) }
            ) { AppImageIcon(app.icon, it) }
        }

        val prefsRequest by vm.openPrefs.collectAsStateWithLifecycle()
        LaunchedEffect(prefsRequest) { if (prefsRequest) { openApp(AppKind.PREFERENCES); vm.openPrefs.value = false } }

        // 2) Desktop icons + floating windows (windows are zIndex-layered by WindowState.zIndex)
        val desktopMenu = listOf(
            MenuEntry("New Finder Window") { openApp(AppKind.FINDER, INTERNAL_ROOT, forceNew = true) },
            MenuEntry.Divider,
            MenuEntry("Change Wallpaper…") { openApp(AppKind.PREFERENCES) },
            MenuEntry(if (settings.showDesktopIcons) "Hide Desktop Icons" else "Show Desktop Icons") { vm.setDesktopIcons(!settings.showDesktopIcons) },
            MenuEntry("Show Launchpad") { vm.setLaunchpad(true) },
            MenuEntry.Divider,
            MenuEntry("System Preferences…") { openApp(AppKind.PREFERENCES) }
        )
        ContextMenuHost(desktopMenu, Modifier.fillMaxSize().padding(top = menuBarH)) {
            if (settings.showDesktopIcons && !compact) {
                val shortcuts = buildList {
                    add(DockEntry("d:internal", "Internal Storage", onClick = { openApp(AppKind.FINDER, INTERNAL_ROOT, forceNew = true, title = "Internal Storage") }) {
                        BuiltinIcon(listOf(Color(0xFF9A9AA0), Color(0xFF58585D)), Icons.Rounded.Storage, it)
                    })
                    settings.storageRoots.forEach { r ->
                        add(DockEntry("d:${r.uri}", r.label, onClick = { openApp(AppKind.FINDER, r.uri, forceNew = true, title = r.label) }) {
                            BuiltinIcon(listOf(Color(0xFFC7CBD1), Color(0xFF8D9299)), Icons.Rounded.Usb, it)
                        })
                    }
                    settings.desktopApps.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }.forEach { add(appEntry(it, it.packageName in running, "d:")) }
                }
                LazyHorizontalGrid(
                    rows = GridCells.Adaptive(100.dp), modifier = Modifier.fillMaxSize().padding(8.dp),
                    reverseLayout = true, userScrollEnabled = false
                ) { items(shortcuts, key = { it.key }) { DesktopIcon(it) } }
            }
            windows.forEach { w ->
                key(w.id) {
                    MacWindowFrame(
                        window = w, ws = ws, focused = w.id == focused?.id,
                        cornerRadius = settings.windowCornerRadius, shadows = settings.windowShadows,
                        actions = WindowActions(
                            onFocus = { wm.focus(w.id) },
                            onClose = { wm.close(w.id) },
                            onMinimize = { wm.minimize(w.id) },
                            onToggleMaximize = { wm.toggleMaximize(w.id) },
                            onMove = { dx, dy -> wm.move(w.id, dx, dy, ws) },
                            onResize = { dx, dy -> wm.resize(w.id, dx, dy, ws) },
                            onDragEnd = { wm.snap(w.id, ws) },
                            onTile = { right -> wm.tile(w.id, ws, right) }
                        ),
                        modifier = Modifier.zIndex(w.zIndex.toFloat())
                    ) { AppContent(w, vm, settings, packs, apps, openImage, openMedia) }
                }
            }
        }

        // 3) Dock
        val dockEntries = buildList {
            add(DockEntry("finder", "Finder", windows.any { it.app == AppKind.FINDER }, onClick = { openApp(AppKind.FINDER) }) {
                BuiltinAppIcon(AppKind.FINDER, it)
            })
            add(DockEntry("launchpad", "Launchpad", onClick = { vm.setLaunchpad(true) }) {
                BuiltinIcon(listOf(Color(0xFFFF9F0A), Color(0xFFFF375F)), Icons.Rounded.GridView, it)
            })
            add(DockEntry("prefs", "System Preferences", windows.any { it.app == AppKind.PREFERENCES }, onClick = { openApp(AppKind.PREFERENCES) }) {
                BuiltinAppIcon(AppKind.PREFERENCES, it)
            })
            settings.pinnedPackages.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
                .forEach { add(appEntry(it, it.packageName in running, "pkg:")) }
        }
        // Apps opened from the launcher stay in the dock (with a dot) until quit, then windows minimized to the dock.
        val runningEntries = running.filter { it !in settings.pinnedPackages }
            .mapNotNull { p -> apps.firstOrNull { it.packageName == p } }.map { appEntry(it, true, "run:") }
        val minimizedEntries = runningEntries + windows.filter { it.isMinimized }.map { w ->
            DockEntry("min:${w.id}", w.title, running = true, onClick = { wm.focus(w.id) }) { BuiltinAppIcon(w.app, it) }
        }
        Dock(
            dockEntries, minimizedEntries, iconSize,
            listOf(
                MenuEntry("Dock Preferences…") { openApp(AppKind.PREFERENCES) },
                MenuEntry.Divider,
                MenuEntry("Make Dock Bigger") { vm.setDockScale((settings.dockScale + 0.15f).coerceAtMost(1.4f)) },
                MenuEntry("Make Dock Smaller") { vm.setDockScale((settings.dockScale - 0.15f).coerceAtLeast(0.7f)) }
            ),
            Modifier.align(Alignment.BottomCenter).zIndex(TOP_LAYER)
                .padding(bottom = bottomInset + 10.dp, start = 12.dp, end = 12.dp)
        )

        // 4) Menu bar
        val appleItems = listOf(
            MenuEntry("About This Tablet") { openApp(AppKind.ABOUT) },
            MenuEntry.Divider,
            MenuEntry("System Preferences…") { openApp(AppKind.PREFERENCES) },
            MenuEntry("Launchpad") { vm.setLaunchpad(true) },
            MenuEntry("Android Settings…") { ctx.safeStart(Intent(android.provider.Settings.ACTION_SETTINGS)) },
            MenuEntry("Set as Default Launcher…") { ctx.safeStart(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) },
            MenuEntry.Divider,
            MenuEntry("Force Quit ${focused?.title.orEmpty()}".trim(), focused != null) { focused?.let { wm.close(it.id) } }
        )
        val menus = if (compact) emptyList() else listOf(
            MenuSpec("File", listOf(
                MenuEntry("New Finder Window") { openApp(AppKind.FINDER, INTERNAL_ROOT, forceNew = true) },
                MenuEntry("Close Window", focused != null) { focused?.let { wm.close(it.id) } }
            )),
            MenuSpec("View", listOf(
                MenuEntry("Show Launchpad") { vm.setLaunchpad(true) },
                MenuEntry(if (dark) "Use Light Mode" else "Use Dark Mode") { vm.toggleDark(dark) }
            )),
            MenuSpec("Window", listOf(
                MenuEntry("Minimize", focused != null) { focused?.let { wm.minimize(it.id) } },
                MenuEntry("Zoom", focused != null) { focused?.let { wm.toggleMaximize(it.id) } },
                MenuEntry.Divider
            ) + windows.map { w -> MenuEntry((if (w.id == focused?.id) "✓ " else "") + w.title) { wm.focus(w.id) } }),
            MenuSpec("Help", listOf(MenuEntry("About This Tablet") { openApp(AppKind.ABOUT) }))
        )
        MenuBar(
            appName = focused?.app?.displayName ?: "Finder",
            compact = compact, topInset = topInset, dockScale = settings.dockScale,
            scale = settings.menuBarScale, notifs = notifs,
            appleItems = appleItems, menus = menus,
            onToggleDark = { vm.toggleDark(dark) }, onDockScale = { vm.setDockScale(it) },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(TOP_LAYER)
        )
    }
}

@Composable
private fun AppContent(w: WindowState, vm: DesktopViewModel, settings: DesktopSettings, packs: List<IconPackInfo>, apps: List<AppInfo>, openImage: (String, String) -> Unit, openMedia: (String, String) -> Unit) {
    when (w.app) {
        AppKind.FINDER -> FinderApp(w.arg, settings.storageRoots, { vm.addStorageRoot(it) }, { vm.removeStorageRoot(it) }, openImage, openMedia, settings.finder) { vm.setFinderPrefs(it) }
        AppKind.PREFERENCES -> PreferencesApp(
            settings, packs, apps,
            PrefActions(
                { vm.setDark(it) }, { vm.setWallpaper(it) }, { vm.setDockScale(it) }, { vm.setDesktopIcons(it) },
                { vm.setWindowCorner(it) }, { vm.setWindowShadows(it) }, { vm.setOpenInWindows(it) }, { vm.setIconPack(it) },
                { vm.setMenuBarScale(it) }, { vm.togglePin(it) }, { pkg, d -> vm.movePin(pkg, d) }
            )
        )
        AppKind.PLAYER -> PlayerApp(w.arg ?: "", w.isMinimized)
        AppKind.IMAGE_EDITOR -> ImageEditorApp(w.arg ?: "")
        AppKind.ABOUT -> AboutApp()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DesktopIcon(e: DockEntry) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.width(88.dp).clip(RoundedCornerShape(8.dp))
                .onSecondaryClick { if (e.menu.isNotEmpty()) menu = true }
                .combinedClickable(onClick = e.onClick, onLongClick = if (e.menu.isNotEmpty()) ({ menu = true }) else null)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            e.icon(52.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                e.label, color = Color.White, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 4f))
            )
        }
        if (menu) DropdownMenu(true, { menu = false }) {
            e.menu.forEach { m -> DropdownMenuItem(text = { Text(m.label) }, onClick = { menu = false; m.onClick() }) }
        }
    }
}
