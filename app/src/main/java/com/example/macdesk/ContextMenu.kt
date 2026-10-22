package com.example.macdesk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Right-click (mouse / trackpad secondary button). Runs on the Main pass so the innermost handler
 * wins: it consumes the press and ancestors (e.g. the desktop menu) don't also fire.
 * Touch users get the same menus via long-press where the element supports it.
 */
fun Modifier.onSecondaryClick(onClick: (Offset) -> Unit): Modifier = composed {
    val cb by rememberUpdatedState(onClick)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Main)
                if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed && e.changes.none { it.isConsumed }) {
                    e.changes.forEach { it.consume() }
                    cb(e.changes.first().position)
                }
            }
        }
    }
}

@Composable
fun ColumnScope.MenuItems(items: List<MenuEntry>, onDone: () -> Unit) {
    items.forEach {
        if (it.label == "---") HorizontalDivider()
        else DropdownMenuItem(text = { Text(it.label) }, enabled = it.enabled, onClick = { onDone(); it.onClick() })
    }
}

/** A menu that opens exactly at [at] (local coordinates of the enclosing Box). */
@Composable
fun BoxScope.ContextMenu(at: Offset?, items: List<MenuEntry>, onDismiss: () -> Unit) {
    if (at == null || items.isEmpty()) return
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(0.dp)) {
        DropdownMenu(true, onDismiss) { MenuItems(items, onDismiss) }
    }
}

@Composable
fun ContextMenuHost(items: List<MenuEntry>, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var at by remember { mutableStateOf<Offset?>(null) }
    Box(modifier.onSecondaryClick { at = it }) {
        content()
        ContextMenu(at, items) { at = null }
    }
}
