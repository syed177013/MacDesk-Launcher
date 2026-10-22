package com.example.macdesk

import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.abs

@Immutable
class IconPackInfo(val pkg: String, val label: String, val icon: ImageBitmap)

/** Intent actions used by ADW / Nova / GO / Apex / Holo-style icon packs. */
val ICON_PACK_ACTIONS = listOf(
    "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
    "com.anddoes.launcher.THEME", "com.dlto.atom.launcher.THEME"
)

fun listIconPacks(ctx: Context): List<IconPackInfo> {
    val pm = ctx.packageManager
    val seen = HashSet<String>()
    return ICON_PACK_ACTIONS.flatMap { pm.queryActivities(android.content.Intent(it)) }
        .mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (!seen.add(pkg)) null else runCatching {
                IconPackInfo(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap())
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
}

/** Parses the pack's appfilter.xml (res/xml or assets) and themes app icons, with back/mask/upon fallback. */
class IconPack(ctx: Context, val pkg: String) {
    private val res: Resources = ctx.packageManager.getResourcesForApplication(pkg)
    private val map = HashMap<String, String>()
    private val backs = ArrayList<String>()
    private var mask: String? = null
    private var upon: String? = null
    private var scale = 1f

    init { runCatching { parse() } }

    private fun parse() {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val p: XmlPullParser = if (id != 0) res.getXml(id)
        else XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("appfilter.xml"), null) }
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) when (p.name) {
                "item" -> {
                    val comp = p.getAttributeValue(null, "component")
                    val d = p.getAttributeValue(null, "drawable")
                    if (comp != null && d != null) {
                        val inner = comp.substringAfter("{", "").substringBefore("}")
                        if (inner.contains("/")) map[inner] = d
                    }
                }
                "iconback" -> for (i in 0 until p.attributeCount)
                    if (p.getAttributeName(i).startsWith("img")) backs.add(p.getAttributeValue(i))
                "iconmask" -> mask = p.getAttributeValue(null, "img1")
                "iconupon" -> upon = p.getAttributeValue(null, "img1")
                "scale" -> scale = p.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
            }
            ev = p.next()
        }
    }

    private fun drawable(name: String): Drawable? = runCatching {
        var id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) id = res.getIdentifier(name, "mipmap", pkg)
        if (id == 0) null else ResourcesCompat.getDrawable(res, id, null)
    }.getOrNull()

    fun icon(cn: ComponentName, original: Drawable, px: Int): Bitmap {
        map["${cn.packageName}/${cn.className}"]?.let { n ->
            drawable(n)?.let { return runCatching { it.toBitmap(px, px) }.getOrElse { original.toBitmap(px, px) } }
        }
        if (backs.isEmpty() && mask == null && upon == null) return original.toBitmap(px, px)
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val full = Rect(0, 0, px, px)
        if (backs.isNotEmpty()) drawable(backs[abs(cn.packageName.hashCode()) % backs.size])?.toBitmap(px, px)?.let { c.drawBitmap(it, null, full, null) }
        val inner = (px * scale).toInt().coerceAtLeast(1)
        val off = ((px - inner) / 2).toFloat()
        c.drawBitmap(original.toBitmap(inner, inner), off, off, null)
        mask?.let { m -> drawable(m)?.toBitmap(px, px)?.let { c.drawBitmap(it, null, full, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }) } }
        upon?.let { u -> drawable(u)?.toBitmap(px, px)?.let { c.drawBitmap(it, null, full, null) } }
        return out
    }
}
