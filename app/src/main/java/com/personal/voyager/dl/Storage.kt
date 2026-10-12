package com.personal.voyager.dl

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Everything lives in /storage/emulated/0/Voyager (needs "All files access", asked once).
 * Partial downloads go to Voyager/.tmp (hidden), so nothing grows inside the app's own data.
 */
object Storage {
    fun granted(): Boolean = Environment.isExternalStorageManager()

    fun root(): File = File(Environment.getExternalStorageDirectory(), "Voyager")
    fun tmp(): File = File(root(), ".tmp")

    fun prepare() {
        root().mkdirs()
        tmp().mkdirs()
        val nomedia = File(tmp(), ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
    }

    fun existsBase(base: String): Boolean =
        root().listFiles()?.any { it.isFile && it.name.startsWith("$base.") } == true

    /** Same volume, so this is a rename (instant), not a copy. */
    fun move(src: File, name: String): File {
        val dest = File(root(), name)
        if (dest.exists()) dest.delete()
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
        return dest
    }

    /** Makes the file visible to gallery and music players. */
    fun scan(ctx: Context, f: File, mime: String?) {
        runCatching { MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf(mime), null) }
    }

    /** Stored location is either an absolute path (current) or a content:// uri (version 1.0.0 files). */
    fun uriFor(ctx: Context, stored: String): Uri =
        if (stored.startsWith("content://")) Uri.parse(stored)
        else FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(stored))

    /**
     * The permanent MediaStore address of a file in the Voyager folder (the same kind any gallery or music app
     * uses). Unlike a FileProvider address it needs no temporary permission, so a player can reopen it at any
     * time, even after the screen that launched it is gone.
     */
    fun mediaUri(ctx: Context, path: String): Uri? = runCatching {
        ctx.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
            MediaStore.Files.FileColumns.DATA + "=?",
            arrayOf(path),
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val id = c.getLong(0)
            when (c.getInt(1)) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                else -> null
            }
        }
    }.getOrNull()

    /** Calls [onReady] with the best address to hand to another app (scans the file first if needed). */
    fun resolveUri(ctx: Context, stored: String, mime: String?, onReady: (Uri) -> Unit) {
        if (stored.startsWith("content://")) {
            onReady(Uri.parse(stored))
            return
        }
        val known = mediaUri(ctx, stored)
        if (known != null) {
            onReady(known)
            return
        }
        val main = Handler(Looper.getMainLooper())
        try {
            MediaScannerConnection.scanFile(ctx, arrayOf(stored), arrayOf(mime)) { _, uri ->
                main.post { onReady(uri ?: mediaUri(ctx, stored) ?: uriFor(ctx, stored)) }
            }
        } catch (e: Exception) {
            onReady(uriFor(ctx, stored))
        }
    }

    fun exists(ctx: Context, stored: String): Boolean =
        if (stored.startsWith("content://")) Saver.uriExists(ctx, Uri.parse(stored)) else File(stored).exists()

    fun delete(ctx: Context, stored: String, mime: String?) {
        if (stored.startsWith("content://")) {
            Saver.delete(ctx, Uri.parse(stored))
        } else {
            val f = File(stored)
            f.delete()
            scan(ctx, f, mime) // removes it from the media database
        }
    }
}
