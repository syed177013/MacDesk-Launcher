package com.example.macdesk

import android.view.LayoutInflater
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Built-in video/audio player (Media3). Uses a TextureView surface so it scales/clips correctly
 * inside animated, rounded windows. Pauses when minimized or when the launcher leaves the foreground.
 */
@Composable
fun PlayerApp(uriStr: String, paused: Boolean) {
    val ctx = LocalContext.current
    val player = remember(uriStr) {
        ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(uriStr)); prepare(); playWhenReady = true }
    }
    var view by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { view?.keepScreenOn = isPlaying }
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(ctx, "Can't play this file", Toast.LENGTH_SHORT).show()
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(paused) { if (paused) player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    AndroidView(
        factory = { c -> LayoutInflater.from(c).inflate(R.layout.player_view, null) as PlayerView },
        update = { v -> v.player = player; view = v },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
}
