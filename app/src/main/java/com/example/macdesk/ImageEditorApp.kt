package com.example.macdesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint as APaint
import android.graphics.Canvas as ACanvas
import android.graphics.Matrix as AMatrix
import android.graphics.ColorMatrix as AColorMatrix
import android.graphics.Path as APath
import android.media.MediaScannerConnection
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Sampled decode honouring EXIF rotation. Shared by the wallpaper loader and the editor. */
fun decodeBitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (o.outWidth / s > maxSide || o.outHeight / s > maxSide) s *= 2
    val b = ctx.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
    } ?: return@runCatching null
    val deg = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        } ?: 0f
    }.getOrDefault(0f)
    if (deg == 0f) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, AMatrix().apply { postRotate(deg) }, true)
}.getOrNull()

private enum class Tool { VIEW, CROP, DRAW, ADJUST }
private class InkStroke(val pts: List<Offset>, val color: Int, val width: Float)

private fun colorArray(b: Float, c: Float, s: Float): FloatArray {
    val cm = AColorMatrix().apply { setSaturation(s) }
    val t = (-0.5f * c + 0.5f + b) * 255f
    cm.postConcat(AColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    return cm.array
}

private fun bake(src: Bitmap, strokes: List<InkStroke>, m: FloatArray): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val c = ACanvas(out)
    c.drawBitmap(src, 0f, 0f, APaint(APaint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(AColorMatrix(m)) })
    val p = APaint(APaint.ANTI_ALIAS_FLAG).apply { style = APaint.Style.STROKE; strokeCap = APaint.Cap.ROUND; strokeJoin = APaint.Join.ROUND }
    strokes.forEach { s ->
        p.color = s.color; p.strokeWidth = s.width
        val path = APath()
        s.pts.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        if (s.pts.size == 1) path.lineTo(s.pts[0].x + 0.1f, s.pts[0].y)
        c.drawPath(path, p)
    }
    return out
}

@Composable
fun ImageEditorApp(uriStr: String) {
    val ctx = LocalContext.current
    val colors = LocalMacColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val uri = remember(uriStr) { Uri.parse(uriStr) }

    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val undo = remember { mutableStateListOf<Bitmap>() }
    var tool by remember { mutableStateOf(Tool.VIEW) }
    var bright by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var sat by remember { mutableFloatStateOf(1f) }
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val live = remember { mutableStateListOf<Offset>() }
    var penColor by remember { mutableIntStateOf(0xFFFF3B30.toInt()) }
    var penSize by remember { mutableFloatStateOf(6f) }
    var crop by remember { mutableStateOf<Rect?>(null) }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var saveMenu by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        val r = withContext(Dispatchers.IO) { decodeBitmap(ctx, uri, 2048) }
        if (r == null) failed = true else bmp = r
    }
    LaunchedEffect(tool) { if (tool != Tool.VIEW) { zoom = 1f; pan = Offset.Zero }; if (tool != Tool.CROP) crop = null }

    val matrix = remember(bright, contrast, sat) { colorArray(bright, contrast, sat) }
    val dirty = bright != 0f || contrast != 1f || sat != 1f || strokes.isNotEmpty()
    val isPng = uriStr.endsWith(".png", true) || (ctx.contentResolver.getType(uri) ?: "").contains("png")

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    fun commit(): Bitmap? {
        val b = bmp ?: return null
        if (!dirty) return b
        val out = bake(b, strokes.toList(), matrix)
        bright = 0f; contrast = 1f; sat = 1f; strokes.clear()
        return out
    }
    fun push(nb: Bitmap) { bmp?.let { undo.add(it); if (undo.size > 4) undo.removeAt(0) }; bmp = nb }
    fun geo(m: AMatrix) { val b = commit() ?: return; push(Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)) }
    fun doUndo() {
        if (dirty) { bright = 0f; contrast = 1f; sat = 1f; strokes.clear() } else undo.removeLastOrNull()?.let { bmp = it }
    }
    fun applyCrop() {
        val r = crop; val b = commit()
        if (r != null && b != null) {
            val l = r.left.toInt().coerceIn(0, b.width - 2); val t = r.top.toInt().coerceIn(0, b.height - 2)
            val w = r.width.toInt().coerceIn(2, b.width - l); val h = r.height.toInt().coerceIn(2, b.height - t)
            push(Bitmap.createBitmap(b, l, t, w, h))
        }
        crop = null; tool = Tool.VIEW
    }
    fun encode(b: Bitmap, os: OutputStream) = b.compress(if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, os)
    val ext = if (isPng) "png" else "jpg"

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(if (isPng) "image/png" else "image/jpeg")) { dest ->
        val b = pendingSave
        if (dest != null && b != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(dest)!!.use { encode(b, it) } }.isSuccess }
            toast(if (ok) "Saved" else "Save failed")
        }
    }
    fun saveCopy() {
        val b = commit() ?: return
        if (uri.scheme == "file") scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(uri.path!!)
                    var n = File(f.parentFile, "${f.nameWithoutExtension}_edited.$ext"); var i = 2
                    while (n.exists()) { n = File(f.parentFile, "${f.nameWithoutExtension}_edited$i.$ext"); i++ }
                    n.outputStream().use { encode(b, it) }
                    MediaScannerConnection.scanFile(ctx, arrayOf(n.path), null, null); n.name
                }.getOrNull()
            }
            toast(if (saved != null) "Saved $saved" else "Save failed (storage access?)")
        } else { pendingSave = b; createDoc.launch("edited.$ext") }
    }
    fun replaceOriginal() {
        val b = commit() ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { encode(b, it) } }.isSuccess }
            toast(if (ok) "Original replaced" else "Can't overwrite this file")
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121214))) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val b = bmp
            when {
                failed -> Text("Can't open this image", color = Color.White)
                b == null -> CircularProgressIndicator()
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val vw = constraints.maxWidth.toFloat(); val vh = constraints.maxHeight.toFloat()
                    val fit = min(vw / b.width, vh / b.height)
                    val org = Offset((vw - b.width * fit) / 2f, (vh - b.height * fit) / 2f)
                    val img = remember(b) { b.asImageBitmap() }
                    fun toImg(o: Offset) = Offset((o.x - org.x) / fit, (o.y - org.y) / fit)
                    fun clampImg(o: Offset) = Offset(o.x.coerceIn(0f, b.width.toFloat()), o.y.coerceIn(0f, b.height.toFloat()))

                    Canvas(
                        Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
                            .pointerInput(tool, fit, org, b) {
                                when (tool) {
                                    Tool.VIEW -> detectTransformGestures { _, p, z, _ ->
                                        zoom = (zoom * z).coerceIn(1f, 8f)
                                        pan = if (zoom == 1f) Offset.Zero else pan + p * zoom
                                    }
                                    Tool.DRAW -> detectDragGestures(
                                        onDragStart = { live.clear(); live.add(toImg(it)) },
                                        onDragEnd = {
                                            if (live.isNotEmpty()) strokes.add(InkStroke(live.toList(), penColor, penSize * density / fit))
                                            live.clear()
                                        },
                                        onDragCancel = { live.clear() }
                                    ) { c, _ -> c.consume(); live.add(toImg(c.position)) }
                                    Tool.CROP -> detectDragGestures(
                                        onDragStart = { anchor = clampImg(toImg(it)); crop = Rect(anchor, anchor) }
                                    ) { c, _ ->
                                        c.consume()
                                        val p = clampImg(toImg(c.position))
                                        crop = Rect(min(anchor.x, p.x), min(anchor.y, p.y), max(anchor.x, p.x), max(anchor.y, p.y))
                                    }
                                    Tool.ADJUST -> Unit
                                }
                            }
                    ) {
                        drawImage(
                            img, dstOffset = IntOffset(org.x.roundToInt(), org.y.roundToInt()),
                            dstSize = IntSize((b.width * fit).roundToInt(), (b.height * fit).roundToInt()),
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix))
                        )
                        fun drawInk(pts: List<Offset>, color: Int, w: Float) {
                            if (pts.isEmpty()) return
                            val path = Path()
                            pts.forEachIndexed { i, o ->
                                val x = org.x + o.x * fit; val y = org.y + o.y * fit
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            if (pts.size == 1) path.lineTo(org.x + pts[0].x * fit + 0.1f, org.y + pts[0].y * fit)
                            drawPath(path, Color(color), style = Stroke(w * fit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        strokes.forEach { drawInk(it.pts, it.color, it.width) }
                        drawInk(live.toList(), penColor, penSize * density / fit)
                        crop?.let { r ->
                            val x = org.x + r.left * fit; val y = org.y + r.top * fit
                            val w = r.width * fit; val h = r.height * fit
                            val dim = Color.Black.copy(alpha = 0.55f)
                            drawRect(dim, Offset.Zero, Size(size.width, y))
                            drawRect(dim, Offset(0f, y + h), Size(size.width, size.height - y - h))
                            drawRect(dim, Offset(0f, y), Size(x, h))
                            drawRect(dim, Offset(x + w, y), Size(size.width - x - w, h))
                            drawRect(Color.White, Offset(x, y), Size(w, h), style = Stroke(2f))
                        }
                    }
                }
            }
        }

        // ----- tool options -----
        if (bmp != null) when (tool) {
            Tool.ADJUST -> Column(Modifier.fillMaxWidth().background(colors.titleBar).padding(horizontal = 16.dp, vertical = 4.dp)) {
                LabeledSlider("Brightness", bright, -0.5f..0.5f) { bright = it }
                LabeledSlider("Contrast", contrast, 0.5f..1.5f) { contrast = it }
                LabeledSlider("Saturation", sat, 0f..2f) { sat = it }
            }
            Tool.DRAW -> Row(Modifier.fillMaxWidth().background(colors.titleBar).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(0xFFFF3B30, 0xFFFFCC00, 0xFF34C759, 0xFF0A84FF, 0xFFFFFFFF, 0xFF000000).forEach { c ->
                    val ci = c.toInt()
                    Box(
                        Modifier.padding(end = 8.dp).size(24.dp).clip(CircleShape).background(Color(ci))
                            .border(if (penColor == ci) 3.dp else 1.dp, if (penColor == ci) colors.accent else Color.Gray, CircleShape)
                            .clickable { penColor = ci }
                    )
                }
                Slider(penSize, { penSize = it }, Modifier.weight(1f), valueRange = 2f..28f)
            }
            Tool.CROP -> Row(Modifier.fillMaxWidth().background(colors.titleBar).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Drag on the image to select an area", Modifier.weight(1f), color = colors.secondary, fontSize = 12.sp)
                TextButton({ tool = Tool.VIEW }) { Text("Cancel") }
                Button({ applyCrop() }, enabled = crop != null) { Text("Crop") }
            }
            Tool.VIEW -> Unit
        }

        // ----- toolbar -----
        Row(Modifier.fillMaxWidth().background(colors.titleBar).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val on = bmp != null
            IconButton({ geo(AMatrix().apply { postRotate(-90f) }) }, enabled = on) { Icon(Icons.Rounded.RotateLeft, "Rotate left", tint = colors.text) }
            IconButton({ geo(AMatrix().apply { postRotate(90f) }) }, enabled = on) { Icon(Icons.Rounded.RotateRight, "Rotate right", tint = colors.text) }
            IconButton({ geo(AMatrix().apply { postScale(-1f, 1f) }) }, enabled = on) { Icon(Icons.Rounded.Flip, "Flip", tint = colors.text) }
            ToolButton(Icons.Rounded.Crop, "Crop", tool == Tool.CROP, on) { tool = if (tool == Tool.CROP) Tool.VIEW else Tool.CROP }
            ToolButton(Icons.Rounded.Brush, "Draw", tool == Tool.DRAW, on) { tool = if (tool == Tool.DRAW) Tool.VIEW else Tool.DRAW }
            ToolButton(Icons.Rounded.Tune, "Adjust", tool == Tool.ADJUST, on) { tool = if (tool == Tool.ADJUST) Tool.VIEW else Tool.ADJUST }
            Spacer(Modifier.weight(1f))
            IconButton({ doUndo() }, enabled = dirty || undo.isNotEmpty()) { Icon(Icons.Rounded.Undo, "Undo", tint = colors.text) }
            Box {
                IconButton({ saveMenu = true }, enabled = on) { Icon(Icons.Rounded.Save, "Save", tint = colors.accent) }
                DropdownMenu(saveMenu, { saveMenu = false }) {
                    DropdownMenuItem(text = { Text("Save a Copy") }, onClick = { saveMenu = false; saveCopy() })
                    DropdownMenuItem(text = { Text("Replace Original") }, onClick = { saveMenu = false; replaceOriginal() })
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalMacColors.current
    IconButton(onClick, enabled = enabled) { Icon(icon, desc, tint = if (active) c.accent else c.text) }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(84.dp), color = LocalMacColors.current.text, fontSize = 12.sp)
        Slider(value, onChange, Modifier.weight(1f), valueRange = range)
    }
}
