package com.example.macdesk

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class WindowActions(
    val onFocus: () -> Unit,
    val onClose: () -> Unit,
    val onMinimize: () -> Unit,
    val onToggleMaximize: () -> Unit,
    val onMove: (dx: Float, dy: Float) -> Unit,      // dp
    val onResize: (dx: Float, dy: Float) -> Unit,    // dp
    val onDragEnd: () -> Unit = {},
    val onTile: (right: Boolean) -> Unit = {}
)

@Composable
fun MacWindowFrame(
    window: WindowState,
    ws: Workspace,
    focused: Boolean,
    cornerRadius: Int,
    shadows: Boolean,
    actions: WindowActions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalMacColors.current
    val act = rememberUpdatedState(actions)
    var interacting by remember { mutableStateOf(false) }
    val maxed = window.isMaximized

    // Snap while dragging/resizing, spring for maximize/restore.
    val spec: AnimationSpec<Dp> = if (interacting) snap() else spring(stiffness = Spring.StiffnessMediumLow)
    val ax by animateDpAsState(if (maxed) 0.dp else window.positionX.dp, spec, label = "x")
    val ay by animateDpAsState(if (maxed) 0.dp else window.positionY.dp, spec, label = "y")
    val aw by animateDpAsState(if (maxed) ws.width.dp else window.width.dp, spec, label = "w")
    val ah by animateDpAsState(if (maxed) ws.maxHeight.dp else window.height.dp, spec, label = "h")
    val x = if (interacting && !maxed) window.positionX.dp else ax
    val y = if (interacting && !maxed) window.positionY.dp else ay
    val w = if (interacting && !maxed) window.width.dp else aw
    val h = if (interacting && !maxed) window.height.dp else ah

    // Minimize genie-ish animation toward the dock. Content stays composed so app state survives.
    val minP by animateFloatAsState(if (window.isMinimized) 0f else 1f, tween(240), label = "min")
    val hidden = window.isMinimized && minP < 0.01f

    // Open / close transitions (close plays before the window is actually removed).
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(closing) { if (closing) { delay(160); act.value.onClose() } }
    val presence by animateFloatAsState(if (shown && !closing) 1f else 0f, tween(if (closing) 150 else 220), label = "presence")

    val shape = RoundedCornerShape(if (maxed) 0.dp else cornerRadius.dp)
    val elevation = when {
        !shadows || window.isMinimized || maxed -> 0.dp
        focused -> 28.dp
        else -> 8.dp
    }

    Box(
        modifier
            .then(if (hidden) Modifier.layout { _, _ -> layout(0, 0) {} } else Modifier)
            .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
            .size(w, h)
            .graphicsLayer {
                alpha = minP * presence
                val s = (0.35f + 0.65f * minP) * (0.93f + 0.07f * presence)
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = (1f - minP) * ((ws.height - window.positionY - window.height).dp.toPx() + 80.dp.toPx())
            }
            .shadow(elevation, shape)
            .clip(shape)
            .background(colors.windowBg)
            .border(0.5.dp, colors.divider, shape)
            .onSecondaryClick { }   // swallow stray right-clicks so the desktop menu never opens through a window
            // Focus elevation: first touch anywhere in the window (observed before children) raises it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.any { it.changedToDown() }) act.value.onFocus()
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            TitleBar(window.title, focused, maxed, act, { closing = true }) { interacting = it }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { content() }
        }
        if (!maxed) ResizeHandle(Modifier.align(Alignment.BottomEnd), act) { interacting = it }
    }
}

@Composable
private fun TitleBar(
    title: String, focused: Boolean, maxed: Boolean,
    act: State<WindowActions>, onClose: () -> Unit, onInteracting: (Boolean) -> Unit
) {
    val colors = LocalMacColors.current
    val density = LocalDensity.current.density
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    Box(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).background(colors.titleBar)
            .onSecondaryClick { menuAt = it }
            .pointerInput(maxed) { detectTapGestures(onDoubleTap = { act.value.onToggleMaximize() }) }
            .pointerInput(maxed) {
                if (!maxed) detectDragGestures(
                    onDragStart = { onInteracting(true) },
                    onDragEnd = { onInteracting(false); act.value.onDragEnd() },
                    onDragCancel = { onInteracting(false) }
                ) { change, drag ->
                    change.consume()
                    act.value.onMove(drag.x / density, drag.y / density)
                }
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            Light(Color(0xFFFF5F57), Icons.Rounded.Close, focused) { onClose() }
            Light(Color(0xFFFEBC2E), Icons.Rounded.Remove, focused) { act.value.onMinimize() }
            Light(Color(0xFF28C840), Icons.Rounded.Add, focused) { act.value.onToggleMaximize() }
        }
        Text(
            title, Modifier.weight(1f), color = if (focused) colors.text else colors.secondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(84.dp))
    }
    ContextMenu(menuAt, listOf(
        MenuEntry("Close Window") { onClose() },
        MenuEntry("Minimize") { act.value.onMinimize() },
        MenuEntry(if (maxed) "Restore" else "Zoom") { act.value.onToggleMaximize() },
        MenuEntry.Divider,
        MenuEntry("Move to Left Half") { act.value.onTile(false) },
        MenuEntry("Move to Right Half") { act.value.onTile(true) }
    )) { menuAt = null }
    }
}

@Composable
private fun Light(color: Color, icon: ImageVector, focused: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(14.dp).clip(CircleShape)
                .background(if (focused) color else Color(0xFF8E8E93).copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (focused) Icon(icon, null, Modifier.size(9.dp), tint = Color.Black.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun ResizeHandle(modifier: Modifier, act: State<WindowActions>, onInteracting: (Boolean) -> Unit) {
    val density = LocalDensity.current.density
    val grip = LocalMacColors.current.secondary
    Canvas(
        modifier.size(32.dp).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { onInteracting(true) },
                onDragEnd = { onInteracting(false) },
                onDragCancel = { onInteracting(false) }
            ) { change, drag ->
                change.consume()
                act.value.onResize(drag.x / density, drag.y / density)
            }
        }
    ) {
        for (i in 1..3) {
            val o = i * 5.dp.toPx()
            drawLine(grip, Offset(size.width - 4.dp.toPx(), size.height - o), Offset(size.width - o, size.height - 4.dp.toPx()), 1.2.dp.toPx())
        }
    }
}
