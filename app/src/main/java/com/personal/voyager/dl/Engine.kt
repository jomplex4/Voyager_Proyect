package com.personal.voyager.dl

import com.personal.voyager.core.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * Downloads one source into a .part file with ranged chunks, resuming from the file size.
 * On 403/410 (expired URL) it asks [refresh] for a new Src and continues from the same offset.
 */
class Fetcher(
    private var src: Src,
    private val refresh: suspend () -> Src,
) {
    private var mode = 0 // 0 = YouTube style (POST + Range header), 1 = GET + "range" parameter
    private var refreshes = 0
    private var rn = 0

    private fun isGoogle(u: String) = u.contains("googlevideo.com") || u.contains("/videoplayback")

    private fun buildRequest(start: Long, end: Long): Request {
        val u = src.url
        val b = Request.Builder().header("User-Agent", src.ua)
        val rangeValue = if (end >= 0) "bytes=$start-$end" else "bytes=$start-"
        if (isGoogle(u)) {
            if (YoutubeParsingHelper.isWebStreamingUrl(u)) {
                b.header("Origin", "https://www.youtube.com")
                b.header("Referer", "https://www.youtube.com/")
            }
            rn++
            if (mode == 0) {
                b.url("$u&rn=$rn")
                b.post(POST_BODY.toRequestBody())
                b.header("Range", rangeValue)
            } else {
                val r = if (end >= 0) "$start-$end" else "$start-"
                b.url("$u&rn=$rn&range=$r")
                b.get()
            }
        } else {
            b.url(u).get().header("Range", rangeValue)
        }
        return b.build()
    }

    private suspend fun probe(): Long = withContext(Dispatchers.IO) {
        Net.client.newCall(buildRequest(0, 0)).execute().use { r ->
            when (r.code) {
                206 -> r.header("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: -1L
                200 -> r.header("Content-Length")?.toLongOrNull() ?: -1L
                else -> throw HttpStatusException(r.code)
            }
        }
    }

    suspend fun into(file: File, onTick: () -> Unit) {
        var total = src.size
        if (total <= 0) total = probe()
        if (total > 0 && file.length() > total) file.delete()
        var failures = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val have = file.length()
            if (total > 0 && have >= total) break
            val end = if (total > 0) minOf(have + CHUNK, total) - 1 else -1L
            try {
                val sawEof = requestChunk(file, have, end, onTick)
                if (total > 0 && file.length() <= have) {
                    // The server answered but sent nothing: back off instead of asking again at full speed.
                    if (++failures > 3) throw IOException("The server returned no data")
                    delay(500L * failures * failures)
                } else failures = 0
                if (total <= 0 && sawEof) break
            } catch (e: HttpStatusException) {
                currentCoroutineContext().ensureActive()
                when {
                    e.code == 416 -> {
                        if (total <= 0 || file.length() >= total) break else throw e
                    }
                    e.code == 401 || e.code == 403 || e.code == 404 || e.code == 410 -> {
                        if (mode == 0 && isGoogle(src.url)) {
                            mode = 1
                        } else if (refreshes < 3) {
                            refreshes++
                            val old = src
                            src = refresh()
                            mode = 0
                            if (src.sig != old.sig) {
                                file.delete()
                                if (src.size > 0) total = src.size
                            }
                        } else throw e
                    }
                    else -> {
                        if (++failures > 3) throw e
                        delay(500L * failures * failures)
                    }
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (++failures > 3) throw e
                delay(500L * failures * failures)
            }
        }
    }

    /** Returns true when the body ended (used when the total size is unknown). */
    private suspend fun requestChunk(file: File, start: Long, end: Long, onTick: () -> Unit): Boolean {
        val call = Net.client.newCall(buildRequest(start, end))
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            return withContext(Dispatchers.IO) {
                call.execute().use { resp ->
                    val code = resp.code
                    if (code == 200 && start > 0) throw IOException("Server ignored the range request")
                    if (code != 206 && code != 200) throw HttpStatusException(code)
                    val body = resp.body ?: throw IOException("Empty response")
                    val append = !(code == 200 && start == 0L)
                    if (!append) file.delete()
                    val buf = ByteArray(64 * 1024)
                    var sinceTick = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(file, true).use { out ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                sinceTick += n
                                if (sinceTick >= 128 * 1024) {
                                    sinceTick = 0
                                    onTick()
                                }
                            }
                        }
                    }
                    onTick()
                    true
                }
            }
        } finally {
            handle?.dispose()
        }
    }

    companion object {
        private const val CHUNK = 10L * 1024 * 1024
        private val POST_BODY = byteArrayOf(0x78, 0x00)
    }
}
