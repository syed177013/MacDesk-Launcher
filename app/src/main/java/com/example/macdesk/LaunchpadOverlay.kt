package com.example.macdesk

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LaunchpadOverlay(
    apps: List<AppInfo>, pinned: Set<String>, onDesktop: Set<String>, topInset: Dp, bottomInset: Dp,
    onLaunch: (AppInfo) -> Unit, onTogglePin: (String) -> Unit, onToggleDesktop: (String) -> Unit, onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val glass = Color.White.copy(alpha = 0.2f)
    val backdrop = LocalBackdrop.current

    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        // Static pre-blurred wallpaper + scrim: looks like Launchpad, costs nothing per frame.
        if (backdrop != null) Image(backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))
        Column(
            Modifier.fillMaxSize().padding(top = topInset + 36.dp, bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                query, { query = it }, Modifier.width(320.dp),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = glass, unfocusedContainerColor = glass,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                    focusedLeadingIconColor = Color.White, unfocusedLeadingIconColor = Color.White
                )
            )
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                GridCells.Adaptive(120.dp), Modifier.fillMaxSize().widthIn(max = 1100.dp),
                contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(filtered, key = { it.packageName }, contentType = { "app" }) { app ->
                    LaunchpadCell(app, app.packageName in pinned, app.packageName in onDesktop, onLaunch, onTogglePin, onToggleDesktop)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LaunchpadCell(app: AppInfo, isPinned: Boolean, onDesk: Boolean, onLaunch: (AppInfo) -> Unit, onTogglePin: (String) -> Unit, onToggleDesktop: (String) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "cell")
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
                .onSecondaryClick { menu = true }
                .combinedClickable(interactionSource = source, indication = null, onClick = { onLaunch(app) }, onLongClick = { menu = true }),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                AppImageIcon(app.icon, 68.dp)
                if (isPinned) Icon(Icons.Rounded.PushPin, "In Dock", Modifier.align(Alignment.BottomEnd).size(16.dp), tint = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        if (menu) DropdownMenu(true, { menu = false }) {
            DropdownMenuItem(text = { Text(if (isPinned) "Remove from Dock" else "Keep in Dock") }, onClick = { menu = false; onTogglePin(app.packageName) })
            DropdownMenuItem(text = { Text(if (onDesk) "Remove from Desktop" else "Add to Desktop") }, onClick = { menu = false; onToggleDesktop(app.packageName) })
            DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onLaunch(app) })
        }
    }
}
