package com.personal.voyager.dl

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Writes finished files into MediaStore (no storage permission needed on Android 10+). */
object Saver {
    private fun collection(audio: Boolean): Uri =
        if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun folder(audio: Boolean) = if (audio) "Music/VOYAGER" else "Movies/VOYAGER"

    fun save(ctx: Context, file: File, displayName: String, mime: String, audio: Boolean): Uri {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder(audio))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection(audio), values) ?: throw IOException("Could not create the file")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out, 256 * 1024) }
            } ?: throw IOException("Could not open the file for writing")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    fun existsInStore(ctx: Context, displayNameNoExt: String, audio: Boolean): Boolean {
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(displayNameNoExt.replace("%", "") + ".%", folder(audio) + "%")
        return runCatching {
            ctx.contentResolver.query(
                collection(audio), arrayOf(MediaStore.MediaColumns._ID), sel, args, null,
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    fun uriExists(ctx: Context, uri: Uri): Boolean = runCatching {
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    fun delete(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.delete(uri, null, null) }
    }
}
