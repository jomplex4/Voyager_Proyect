package com.personal.voyager.fb

import android.content.Context
import com.personal.voyager.core.Net
import com.personal.voyager.dl.Storage
import okhttp3.Request
import java.io.File

/** Saves a Facebook photo into the Voyager folder (visible in the gallery). */
object Photo {
    fun save(ctx: Context, url: String): Boolean = runCatching {
        Storage.prepare()
        val req = Request.Builder().url(url).header("User-Agent", Net.UA_CHROME).build()
        Net.client.newCall(req).execute().use { r ->
            val input = r.body?.byteStream()
            if (!r.isSuccessful || input == null) return false
            val f = File(Storage.root(), "Facebook photo ${System.currentTimeMillis()}.jpg")
            input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            Storage.scan(ctx, f, "image/jpeg")
            true
        }
    }.getOrDefault(false)
}
