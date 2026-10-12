package com.personal.voyager.dl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.personal.voyager.core.Links
import com.personal.voyager.core.Net
import okhttp3.Request
import java.io.ByteArrayOutputStream

/** Square cover art for audio files (embedded in the tags) and for the Play list. */
object Cover {
    class Result(val tag: ByteArray, val thumb: ByteArray)

    private fun download(url: String): ByteArray? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", Net.UA_DESKTOP).build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.bytes()
        }
    }.getOrNull()

    private fun jpeg(b: Bitmap, q: Int): ByteArray {
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, q, o)
        return o.toByteArray()
    }

    fun fetch(job: DlJob): Result? {
        val urls = ArrayList<String>()
        if (job.source == "yt") {
            Links.youtubeId(job.url)?.let { id ->
                urls.add("https://i.ytimg.com/vi/$id/maxresdefault.jpg")
                urls.add("https://i.ytimg.com/vi/$id/hq720.jpg")
            }
        }
        job.thumb?.let { urls.add(it) }
        for (u in urls) {
            val bytes = download(u) ?: continue
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (bmp.width < 200 || bmp.height < 200) {
                bmp.recycle()
                continue
            }
            val s = minOf(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s)
            val big = Bitmap.createScaledBitmap(square, 500, 500, true)
            val small = Bitmap.createScaledBitmap(square, 320, 320, true)
            val result = Result(jpeg(big, 88), jpeg(small, 82))
            if (big !== square) big.recycle()
            small.recycle()
            if (square !== bmp) square.recycle()
            bmp.recycle()
            return result
        }
        return null
    }
}
