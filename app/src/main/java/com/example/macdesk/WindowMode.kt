package com.example.macdesk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Android only draws other apps as floating windows when the system's freeform mode is on.
 * This helper reads that state and (with WRITE_SECURE_SETTINGS, granted once over ADB) turns it on.
 */
object WindowMode {
    private const val FREEFORM = "enable_freeform_support"
    private const val RESIZABLE = "force_resizable_activities"

    fun adbCommand(ctx: Context) = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun canWriteSecure(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun flag(ctx: Context, key: String) = runCatching { Settings.Global.getInt(ctx.contentResolver, key, 0) == 1 }.getOrDefault(false)

    fun freeformOn(ctx: Context) = flag(ctx, FREEFORM) || freeformSupported(ctx)
    fun resizableOn(ctx: Context) = flag(ctx, RESIZABLE)
    fun ready(ctx: Context) = freeformOn(ctx)

    fun enable(ctx: Context): Boolean = runCatching {
        Settings.Global.putInt(ctx.contentResolver, FREEFORM, 1)
        Settings.Global.putInt(ctx.contentResolver, RESIZABLE, 1)
        true
    }.getOrDefault(false)
}
