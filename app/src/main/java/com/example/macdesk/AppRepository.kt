package com.example.macdesk

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
class AppInfo(val label: String, val packageName: String, val activity: String, val icon: ImageBitmap)

fun PackageManager.queryActivities(i: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= 33) queryIntentActivities(i, PackageManager.ResolveInfoFlags.of(0))
    else @Suppress("DEPRECATION") queryIntentActivities(i, 0)

class AppRepository(private val context: Context) {

    suspend fun loadApps(iconPack: String?): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val pack = iconPack?.let { runCatching { IconPack(context, it) }.getOrNull() }
        pm.queryActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { ri ->
                runCatching {
                    val pkg = ri.activityInfo.packageName
                    val original = ri.loadIcon(pm)
                    val bmp = pack?.icon(ComponentName(pkg, ri.activityInfo.name), original, 128) ?: original.toBitmap(128, 128)
                    AppInfo(ri.loadLabel(pm).toString(), pkg, ri.activityInfo.name, bmp.asImageBitmap())
                }.getOrNull()
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Launches an app. With [bounds] set, the activity is started with launch bounds so that
     * freeform-capable systems (DeX, Android desktop mode, "Enable freeform windows") open it as a window.
     */
    fun launch(app: AppInfo, bounds: Rect?) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opts = bounds?.let {
            val ao = ActivityOptions.makeBasic().setLaunchBounds(it)
            // Hidden API; honoured on some builds, harmlessly absent on others.
            runCatching { ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(ao, 5) }
            ao.toBundle()
        }
        runCatching { context.startActivity(intent, opts) }
            .onFailure { Toast.makeText(context, "Can't open ${app.label}", Toast.LENGTH_SHORT).show() }
    }
}

fun freeformSupported(ctx: Context): Boolean =
    ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
