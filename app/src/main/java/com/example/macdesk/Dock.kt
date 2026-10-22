package com.example.macdesk

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class DockEntry(
    val key: String,
    val label: String,
    val running: Boolean = false,
    val menu: List<MenuEntry> = emptyList(),
    val onClick: () -> Unit,
    val icon: @Composable (Dp) -> Unit
)

@Composable
fun BuiltinIcon(gradient: List<Color>, icon: ImageVector, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.23f)).background(Brush.verticalGradient(gradient)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(size * 0.55f), tint = Color.White) }
}

@Composable
fun BuiltinAppIcon(kind: AppKind, size: Dp) {
    val themed = LocalBuiltinIcons.current[kind.name]
    if (themed != null) AppImageIcon(themed, size) else DefaultBuiltinIcon(kind, size)
}

@Composable
private fun DefaultBuiltinIcon(kind: AppKind, size: Dp) = when (kind) {
    AppKind.FINDER -> BuiltinIcon(listOf(Color(0xFF5AC8FA), Color(0xFF0A84FF)), Icons.Rounded.Folder, size)
    AppKind.PREFERENCES -> BuiltinIcon(listOf(Color(0xFF9A9AA0), Color(0xFF58585D)), Icons.Rounded.Settings, size)
    AppKind.PLAYER -> BuiltinIcon(listOf(Color(0xFFFF6B6B), Color(0xFFD63031)), Icons.Rounded.PlayArrow, size)
    AppKind.IMAGE_EDITOR -> BuiltinIcon(listOf(Color(0xFF30D158), Color(0xFF0A9F7A)), Icons.Rounded.Image, size)
    AppKind.ABOUT -> BuiltinIcon(listOf(Color(0xFF64D2FF), Color(0xFF5E5CE6)), Icons.Rounded.Info, size)
}

@Composable
fun AppImageIcon(bitmap: ImageBitmap, size: Dp) {
    Image(bitmap, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.23f)))
}

@Composable
fun Dock(entries: List<DockEntry>, minimized: List<DockEntry>, iconSize: Dp, bgMenu: List<MenuEntry>, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        GlassSurface(shape = RoundedCornerShape(22.dp)) {
            ContextMenuHost(bgMenu) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                entries.forEach { key(it.key) { DockItem(it, iconSize) } }
                if (minimized.isNotEmpty()) {
                    Box(Modifier.width(1.dp).height(iconSize).background(Color.White.copy(alpha = 0.35f)))
                    minimized.forEach { key(it.key) { DockItem(it, iconSize) } }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(e: DockEntry, size: Dp) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 1.22f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "dock"
    )
    val bounce = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val dot = LocalMacColors.current.menuText.copy(alpha = 0.85f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier.size(size)
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationY = bounce.value * density }
                    .onSecondaryClick { if (e.menu.isNotEmpty()) menuOpen = true }
                    .combinedClickable(
                        interactionSource = source, indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { bounce.animateTo(-14f, tween(110)); bounce.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
                            e.onClick()
                        },
                        onLongClick = if (e.menu.isNotEmpty()) ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuOpen = true }) else null
                    )
            ) { e.icon(size) }
            if (menuOpen) DropdownMenu(true, { menuOpen = false }) {
                Text(e.label, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), maxLines = 1)
                HorizontalDivider()
                e.menu.forEach { m ->
                    DropdownMenuItem(text = { Text(m.label) }, onClick = { menuOpen = false; m.onClick() })
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (e.running) dot else Color.Transparent))
    }
}
