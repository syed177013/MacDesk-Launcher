package com.example.macdesk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val APP_ROOT = "app"
const val INTERNAL_ROOT = "internal"

data class FileEntry(
    val doc: DocumentFile, val name: String, val isDir: Boolean,
    val size: Long, val modified: Long, val mime: String?
) {
    val isImage get() = mime?.startsWith("image/") == true
    val isVideo get() = mime?.startsWith("video/") == true
    val isAudio get() = mime?.startsWith("audio/") == true
}

class Loc(val doc: DocumentFile, val name: String, val parent: Loc?, val rootId: String) {
    val path: List<String> get() = (parent?.path ?: emptyList()) + name
}

class StorageRepository(private val ctx: Context) {
    private val ext: File get() = Environment.getExternalStorageDirectory()

    fun internalRoot(): DocumentFile = DocumentFile.fromFile(ext)
    fun appStorage(): DocumentFile? = ctx.getExternalFilesDir(null)?.let { DocumentFile.fromFile(it) }
    fun favoriteFolders(): List<String> =
        listOf("Download", "DCIM", "Pictures", "Documents", "Music", "Movies").filter { File(ext, it).isDirectory }
    fun folder(name: String): DocumentFile = DocumentFile.fromFile(File(ext, name))

    fun fromTree(uri: String): DocumentFile? = runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(uri)) }.getOrNull()

    /** True when real file access to shared storage is granted ("All files access" on Android 11+). */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun allFilesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    suspend fun list(dir: DocumentFile): List<FileEntry> = withContext(Dispatchers.IO) {
        dir.listFiles().map {
            FileEntry(it, it.name ?: "Untitled", it.isDirectory, it.length(), it.lastModified(), it.type)
        }.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun persist(uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun removableVolumes(): List<StorageVolume> =
        ctx.getSystemService(StorageManager::class.java).storageVolumes.filter { !it.isPrimary && it.state == "mounted" }

    /** Direct file access to a volume (API 30+). Null on older systems -> use SAF grant. */
    fun volumeRoot(v: StorageVolume): DocumentFile? =
        if (Build.VERSION.SDK_INT >= 30) v.directory?.let { DocumentFile.fromFile(it) } else null

    fun grantIntent(volume: StorageVolume?): Intent =
        if (volume != null && Build.VERSION.SDK_INT >= 29) volume.createOpenDocumentTreeIntent()
        else Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
}
