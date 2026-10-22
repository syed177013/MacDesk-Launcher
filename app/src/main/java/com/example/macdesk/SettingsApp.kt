package com.example.macdesk

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

const val GITHUB_URL = "https://github.com/syed177013/MacDesk-Launcher"

class PrefActions(
    val onDark: (Boolean) -> Unit, val onWallpaper: (String?) -> Unit, val onDockScale: (Float) -> Unit,
    val onDesktopIcons: (Boolean) -> Unit, val onCorner: (Int) -> Unit, val onShadows: (Boolean) -> Unit,
    val onOpenInWindows: (Boolean) -> Unit, val onIconPack: (String?) -> Unit,
    val onMenuBarScale: (Float) -> Unit, val onTogglePin: (String) -> Unit, val onMovePin: (String, Int) -> Unit
)

@Composable
fun PreferencesApp(settings: DesktopSettings, packs: List<IconPackInfo>, apps: List<AppInfo>, a: PrefActions) {
    val ctx = LocalContext.current
    val colors = LocalMacColors.current
    val dark = LocalDark.current
    val wallpaperContent = LocalWallpaperContent.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            a.onWallpaper(uri.toString())
        }
    }
    var dock by remember(settings.dockScale) { mutableFloatStateOf(settings.dockScale) }
    var corner by remember(settings.windowCornerRadius) { mutableFloatStateOf(settings.windowCornerRadius.toFloat()) }
    var menuBar by remember(settings.menuBarScale) { mutableFloatStateOf(settings.menuBarScale) }
    var pickDock by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    @Suppress("UNUSED_EXPRESSION") tick

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Section("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!dark, { a.onDark(false) }, { Text("Light") })
                FilterChip(dark, { a.onDark(true) }, { Text("Dark") })
            }
        }
        Section("Wallpaper") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(144.dp, 90.dp).clip(RoundedCornerShape(8.dp))) { wallpaperContent() }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button({ picker.launch(arrayOf("image/*")) }) { Text("Choose Picture…") }
                    TextButton({ a.onWallpaper(null) }, enabled = settings.wallpaperUri != null) { Text("Use Default") }
                }
            }
        }
        Section("Icon Pack") {
            Text("Works with ADW, Nova, Apex and GO-style icon packs installed on this device.", fontSize = 12.sp, color = colors.secondary)
            PackRow("System default", null, settings.iconPack == null) { a.onIconPack(null) }
            packs.forEach { p -> PackRow(p.label, p, settings.iconPack == p.pkg) { a.onIconPack(p.pkg) } }
            if (packs.isEmpty()) Text("No icon packs found.", fontSize = 12.sp, color = colors.secondary)
            TextButton({ ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps"))) }) { Text("Get Icon Packs…") }
        }
        Section("Window Mode") {
            ToggleRow("Open apps in windows", settings.openInWindows, a.onOpenInWindows)
            val ready = WindowMode.ready(ctx)
            Text(
                if (ready) "Freeform windows are on. Apps open as floating windows (restart the device if they still open full-screen)."
                else "Freeform windows are off, so Android opens apps full-screen. Turn them on once:",
                fontSize = 12.sp, color = if (ready) colors.secondary else colors.accent
            )
            if (!ready || !WindowMode.resizableOn(ctx)) {
                if (WindowMode.canWriteSecure(ctx)) {
                    Button({
                        val ok = WindowMode.enable(ctx); tick++
                        Toast.makeText(ctx, if (ok) "Enabled. Restart your device to apply." else "Couldn't change settings", Toast.LENGTH_LONG).show()
                    }) { Text("Enable Window Mode") }
                } else {
                    Text("Option 1 — Developer options: turn on “Enable freeform windows” and “Force activities to be resizable”, then restart.", fontSize = 12.sp, color = colors.secondary)
                    OutlinedButton({ ctx.safeStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }) { Text("Open Developer Options…") }
                    Text("Option 2 — one-tap enabling: run this once from a computer, then come back here.", fontSize = 12.sp, color = colors.secondary)
                    Text(WindowMode.adbCommand(ctx), fontSize = 11.sp, color = colors.text,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.sidebar).padding(8.dp))
                    OutlinedButton({
                        (ctx.getSystemService(android.content.ClipboardManager::class.java))
                            .setPrimaryClip(android.content.ClipData.newPlainText("adb", WindowMode.adbCommand(ctx)))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy Command") }
                }
            }
        }
        Section("Menu Bar") {
            Text("Size", fontSize = 12.sp, color = colors.secondary)
            Slider(menuBar, { menuBar = it }, valueRange = 0.8f..1.5f, onValueChangeFinished = { a.onMenuBarScale(menuBar) })
        }
        Section("Dock") {
            Text("Icon size", fontSize = 12.sp, color = colors.secondary)
            Slider(dock, { dock = it }, valueRange = 0.7f..1.4f, onValueChangeFinished = { a.onDockScale(dock) })
            Text("Pinned apps", fontSize = 12.sp, color = colors.secondary)
            val pinned = settings.pinnedPackages.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
            pinned.forEachIndexed { i, app ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, Modifier.weight(1f), color = colors.text, fontSize = 14.sp, maxLines = 1)
                    IconButton({ a.onMovePin(app.packageName, -1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move left") }
                    IconButton({ a.onMovePin(app.packageName, 1) }, enabled = i < pinned.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move right") }
                    IconButton({ a.onTogglePin(app.packageName) }) { Icon(Icons.Rounded.Close, "Remove from Dock") }
                }
            }
            if (pinned.isEmpty()) Text("Nothing pinned yet. Add apps here or long-press an app in Launchpad.", fontSize = 12.sp, color = colors.secondary)
            OutlinedButton({ pickDock = true }) { Text("Add App to Dock…") }
        }
        Section("Notifications") {
            val granted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName).also { tick }
            Text(
                if (granted) "The menu bar bell shows how many notifications are active."
                else "Allow notification access so the menu bar bell can show a count. Tapping it opens the Android notification shade.",
                fontSize = 12.sp, color = colors.secondary
            )
            if (!granted) OutlinedButton({ ctx.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Grant Notification Access…") }
        }
        Section("Windows") {
            Text("Corner radius", fontSize = 12.sp, color = colors.secondary)
            Slider(corner, { corner = it }, valueRange = 0f..24f, onValueChangeFinished = { a.onCorner(corner.toInt()) })
            ToggleRow("Window shadows", settings.windowShadows, a.onShadows)
            ToggleRow("Show desktop icons", settings.showDesktopIcons, a.onDesktopIcons)
        }
        Section("Launcher") {
            OutlinedButton({ ctx.safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) }) { Text("Set as Default Home App…") }
        }
    }
    if (pickDock) AppPickerDialog(apps.filter { it.packageName !in settings.pinnedPackages }, { a.onTogglePin(it.packageName) }) { pickDock = false }
}

@Composable
private fun AppPickerDialog(apps: List<AppInfo>, onPick: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(apps, q) { if (q.isBlank()) apps else apps.filter { it.label.contains(q, true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Dock") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(320.dp)) {
                    items(list.size, key = { list[it].packageName }) { i ->
                        val app = list[i]
                        Row(Modifier.fillMaxWidth().clickable { onPick(app); onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(app.icon, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(app.label, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } }
    )
}

@Composable
private fun PackRow(label: String, pack: IconPackInfo?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected, onClick)
        if (pack != null) { Image(pack.icon, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))); Spacer(Modifier.width(10.dp)) }
        Text(label, color = LocalMacColors.current.text, fontSize = 14.sp)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LocalMacColors.current.text)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = LocalMacColors.current.text, fontSize = 14.sp)
        Switch(value, onChange)
    }
}

@Composable
fun AboutApp() {
    val ctx = LocalContext.current
    val colors = LocalMacColors.current
    val cfg = LocalConfiguration.current
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BuiltinIcon(listOf(Color(0xFF64D2FF), Color(0xFF5E5CE6)), Icons.Rounded.Folder, 72.dp)
        Spacer(Modifier.height(12.dp))
        Text("MacDesk", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Text("Version 1.0", fontSize = 12.sp, color = colors.secondary)
        Spacer(Modifier.height(14.dp))
        Text("${Build.MANUFACTURER} ${Build.MODEL}", color = colors.text, fontSize = 14.sp)
        Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", color = colors.secondary, fontSize = 12.sp)
        Text("Display ${cfg.screenWidthDp} × ${cfg.screenHeightDp} dp", color = colors.secondary, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Text("Made by syed177013", color = colors.text, fontSize = 13.sp)
        Text(
            "github.com/syed177013/MacDesk-Launcher",
            Modifier.clip(RoundedCornerShape(6.dp)).clickable { ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))) }.padding(horizontal = 8.dp, vertical = 6.dp),
            color = colors.accent, fontSize = 13.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
        )
    }
}
