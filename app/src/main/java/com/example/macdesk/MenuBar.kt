package com.example.macdesk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MenuEntry(val label: String, val enabled: Boolean = true, val onClick: () -> Unit = {}) {
    companion object { val Divider = MenuEntry("---") }
}
class MenuSpec(val title: String, val items: List<MenuEntry>)
data class BatteryInfo(val level: Int, val charging: Boolean)

fun Context.safeStart(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun MenuBar(
    appName: String,
    compact: Boolean,
    topInset: Dp,
    dockScale: Float,
    scale: Float,
    notifs: List<NotifItem>,
    appleItems: List<MenuEntry>,
    menus: List<MenuSpec>,
    onToggleDark: () -> Unit,
    onDockScale: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalDark.current
    val fg = if (dark) Color.White else Color(0xFF111111)
    val online by rememberOnline()
    val battery by rememberBattery()
    val clock by rememberClock(if (compact) "h:mm a" else "EEE d MMM  h:mm a")

    GlassSurface(modifier.fillMaxWidth().height(topInset + 30.dp * scale), RectangleShape) {
        Row(
            Modifier.fillMaxSize().padding(top = topInset).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MenuButton(appleItems) { Icon(Icons.Rounded.Eco, "Menu", Modifier.size((17 * scale).dp), tint = fg) }
            MenuButton(emptyList()) { Text(appName, color = fg, fontSize = (13 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            menus.forEach { m -> MenuButton(m.items) { Text(m.title, color = fg, fontSize = (13 * scale).sp) } }

            Spacer(Modifier.weight(1f))

            Icon(if (online) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, null, Modifier.size((16 * scale).dp), tint = fg)
            Spacer(Modifier.width(12.dp))
            BatteryIndicator(battery, fg, showText = !compact)
            Spacer(Modifier.width(10.dp))
            ControlCenter(fg, dark, dockScale, onToggleDark, onDockScale)
            NotificationBell(notifs, fg, scale)
            Icon(
                if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "Toggle theme",
                Modifier.clip(RoundedCornerShape(4.dp)).clickable { onToggleDark() }.padding(4.dp).size((16 * scale).dp),
                tint = fg
            )
            Spacer(Modifier.width(8.dp))
            Text(clock, color = fg, fontSize = (13 * scale).sp, maxLines = 1)
        }
    }
}

@Composable
private fun NotificationBell(items: List<NotifItem>, fg: Color, scale: Float) {
    val ctx = LocalContext.current
    var panel by remember { mutableStateOf(false) }
    val count = items.size
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(4.dp))
                .clickable { if (!NotificationShade.open(ctx)) panel = true }   // native shade; own panel as fallback
                .padding(horizontal = 5.dp, vertical = 3.dp)
        ) {
            Icon(if (count > 0) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone, "Notifications", Modifier.size((17 * scale).dp), tint = fg)
            if (count > 0) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-5).dp).clip(RoundedCornerShape(50))
                    .background(Color(0xFFFF3B30)).padding(horizontal = 4.dp)
            ) { Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 9.sp, maxLines = 1) }
        }
        if (panel) DropdownMenu(true, { panel = false }, Modifier.width(320.dp)) {
            val access = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
            when {
                !access -> DropdownMenuItem(text = { Text("Allow notification access…") }, onClick = {
                    panel = false; ctx.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
                items.isEmpty() -> Text("No notifications", Modifier.padding(16.dp), fontSize = 13.sp)
                else -> {
                    items.take(15).forEach { n ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(n.app, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text(n.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    if (n.text.isNotBlank()) Text(n.text, fontSize = 12.sp, maxLines = 2)
                                }
                            },
                            onClick = { panel = false; runCatching { n.intent?.send() } }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Clear All") }, onClick = { panel = false; NotificationHub.service?.cancelAllNotifications() })
                }
            }
        }
    }
}

@Composable
private fun MenuButton(items: List<MenuEntry>, label: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(4.dp))
                .clickable(enabled = items.isNotEmpty()) { open = true }
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) { label() }
        DropdownMenu(open, { open = false }) {
            items.forEach {
                if (it.label == "---") HorizontalDivider()
                else DropdownMenuItem(text = { Text(it.label) }, enabled = it.enabled, onClick = { open = false; it.onClick() })
            }
        }
    }
}

@Composable
private fun ControlCenter(fg: Color, dark: Boolean, dockScale: Float, onToggleDark: () -> Unit, onDockScale: (Float) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var dock by remember(dockScale) { mutableFloatStateOf(dockScale) }
    Box {
        Icon(
            Icons.Rounded.Tune, "Control Center",
            Modifier.clip(RoundedCornerShape(4.dp)).clickable { open = true }.padding(4.dp).size(16.dp), tint = fg
        )
        DropdownMenu(open, { open = false }, Modifier.width(300.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(Icons.Rounded.Wifi, "Wi-Fi", true, Modifier.weight(1f)) {
                        ctx.safeStart(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS))
                    }
                    Tile(Icons.Rounded.Bluetooth, "Bluetooth", false, Modifier.weight(1f)) {
                        ctx.safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(if (dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, if (dark) "Dark" else "Light", dark, Modifier.weight(1f)) { onToggleDark() }
                    Tile(Icons.Rounded.MusicNote, "Sound", false, Modifier.weight(1f)) {
                        ctx.safeStart(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS))
                    }
                }
                Tile(Icons.Rounded.Brightness6, "Display settings", false, Modifier.fillMaxWidth()) {
                    ctx.safeStart(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }
                Text("Dock size", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(dock, { dock = it }, valueRange = 0.7f..1.4f, onValueChangeFinished = { onDockScale(dock) })
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun BatteryIndicator(b: BatteryInfo, fg: Color, showText: Boolean) {
    val fill = when { b.charging -> Color(0xFF30D158); b.level <= 20 -> Color(0xFFFF453A); else -> fg }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showText) { Text("${b.level}%", color = fg, fontSize = 12.sp); Spacer(Modifier.width(5.dp)) }
        Canvas(Modifier.size(24.dp, 12.dp)) {
            val bodyW = size.width - 3.dp.toPx()
            val outline = fg.copy(alpha = 0.9f)
            drawRoundRect(outline, size = Size(bodyW, size.height), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawRoundRect(
                fill, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(((bodyW - 4.dp.toPx()) * b.level / 100f).coerceAtLeast(0f), size.height - 4.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
            drawRoundRect(outline, Offset(bodyW + 0.5.dp.toPx(), size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f), CornerRadius(1.dp.toPx()))
        }
        if (b.charging) Icon(Icons.Rounded.Bolt, null, Modifier.size(12.dp), tint = fg)
    }
}

@Composable
private fun rememberClock(pattern: String): State<String> = produceState("", pattern) {
    val f = SimpleDateFormat(pattern, Locale.getDefault())
    while (true) { value = f.format(Date()); delay(15_000) }
}

@Composable
fun rememberBattery(): State<BatteryInfo> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(readBattery(ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { state.value = readBattery(i) }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    return state
}

private fun readBattery(i: Intent?): BatteryInfo {
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val pct = if (level < 0 || scale <= 0) 100 else level * 100 / scale
    return BatteryInfo(pct, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}

@Composable
fun rememberOnline(): State<Boolean> {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(ConnectivityManager::class.java) }
    val state = remember { mutableStateOf(cm.activeNetwork != null) }
    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = true }
            override fun onLost(network: Network) { state.value = false }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return state
}
