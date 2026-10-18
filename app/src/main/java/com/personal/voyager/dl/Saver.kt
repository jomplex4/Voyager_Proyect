package com.personal.voyager.dl

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Only used for files saved by version 1.0.0 (MediaStore). New downloads use [Storage]. */
object Saver {
    fun uriExists(ctx: Context, uri: Uri): Boolean = runCatching {
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    fun delete(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.delete(uri, null, null) }
    }
}
