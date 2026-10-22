package com.example.macdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Frosted glass without any live blur. The wallpaper is blurred ONCE into a tiny bitmap
 * ([LocalBackdrop]); each surface just draws its slice of it plus a tint. Cost is near zero,
 * which fixes the battery drain and stutter caused by per-frame RenderEffect blurs.
 */
@Composable
fun GlassSurface(modifier: Modifier = Modifier, shape: Shape, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDark.current
    val backdrop = LocalBackdrop.current
    val root = LocalRootSize.current
    var pos by remember { mutableStateOf(IntOffset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val tint = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.30f)
    val edge = if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.6f)

    Box(
        modifier
            .onGloballyPositioned {
                val p = it.positionInRoot()
                pos = IntOffset(p.x.roundToInt(), p.y.roundToInt()); boxSize = it.size
            }
            .clip(shape)
            .drawBehind {
                if (backdrop != null && root.width > 0 && root.height > 0) {
                    val bw = backdrop.width; val bh = backdrop.height
                    val scale = max(root.width.toFloat() / bw, root.height.toFloat() / bh)
                    val ox = (root.width - bw * scale) / 2f
                    val oy = (root.height - bh * scale) / 2f
                    val sx = ((pos.x - ox) / scale).roundToInt().coerceIn(0, bw - 1)
                    val sy = ((pos.y - oy) / scale).roundToInt().coerceIn(0, bh - 1)
                    val sw = (size.width / scale).roundToInt().coerceIn(1, bw - sx)
                    val sh = (size.height / scale).roundToInt().coerceIn(1, bh - sy)
                    drawImage(
                        backdrop, IntOffset(sx, sy), IntSize(sw, sh),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Low
                    )
                }
                drawRect(tint)
            }
            .border(0.8.dp, edge, shape),
        content = content
    )
}

@Composable
fun WallpaperLayer(bitmap: ImageBitmap?) {
    if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxSize().background(Color.Black))
}

class WallpaperSet(val full: ImageBitmap, val blurred: ImageBitmap)

suspend fun loadWallpaperSet(ctx: Context, uri: Uri?, dark: Boolean): WallpaperSet = withContext(Dispatchers.Default) {
    val bmp = uri?.let { withContext(Dispatchers.IO) { decodeBitmap(ctx, it, 1920) } } ?: gradientBitmap(dark)
    WallpaperSet(bmp.asImageBitmap(), blurSmall(bmp).asImageBitmap())
}

private fun gradientBitmap(dark: Boolean): Bitmap {
    val w = 360; val h = 225
    val colors = if (dark) intArrayOf(0xFF0B1026.toInt(), 0xFF2B1B5A.toInt(), 0xFF0F6B8F.toInt())
    else intArrayOf(0xFFFFC796.toInt(), 0xFFFF6B95.toInt(), 0xFF6C5CE7.toInt())
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(b).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP) })
    return b
}

private fun blurSmall(src: Bitmap): Bitmap {
    val w = 160
    val h = (src.height * 160f / src.width).roundToInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val a = IntArray(w * h); val t = IntArray(w * h)
    small.getPixels(a, 0, w, 0, 0, w, h)
    repeat(3) { pass(a, t, w, h, 5, true); pass(t, a, w, h, 5, false) }
    return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
}

private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    val win = 2 * r + 1
    for (l in 0 until lines) {
        fun idx(i: Int) = if (horizontal) l * w + i else i * w + l
        var rs = 0; var gs = 0; var bs = 0
        for (i in -r..r) { val p = src[idx(i.coerceIn(0, len - 1))]; rs += (p shr 16) and 255; gs += (p shr 8) and 255; bs += p and 255 }
        for (i in 0 until len) {
            dst[idx(i)] = (0xFF shl 24) or ((rs / win) shl 16) or ((gs / win) shl 8) or (bs / win)
            val add = src[idx((i + r + 1).coerceAtMost(len - 1))]
            val rem = src[idx((i - r).coerceAtLeast(0))]
            rs += ((add shr 16) and 255) - ((rem shr 16) and 255)
            gs += ((add shr 8) and 255) - ((rem shr 8) and 255)
            bs += (add and 255) - (rem and 255)
        }
    }
}
