package com.personal.voyager.fb

import android.net.Uri
import android.text.Html
import com.personal.voyager.core.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException

/** Public Facebook videos only (no login). Isolated here because Facebook changes its pages often. */
object Facebook {
    class Info(
        val pageUrl: String,
        val title: String,
        val thumb: String?,
        val hdUrl: String?,
        val sdUrl: String?,
        val durationSec: Long,
    )

    class FbException(msg: String) : Exception(msg)

    const val NOT_AVAILABLE = "This video is private, needs login, or is not supported."

    private val hdKeys = listOf("playable_url_quality_hd", "browser_native_hd_url", "hd_src_no_ratelimit", "hd_src")
    private val sdKeys = listOf("playable_url", "browser_native_sd_url", "sd_src_no_ratelimit", "sd_src")

    suspend fun fetch(inputUrl: String): Info = withContext(Dispatchers.IO) {
        val first = tryFetch(inputUrl, Net.UA_CHROME)
        if (first != null) return@withContext first
        val second = tryFetch(mobileVariant(inputUrl), Net.UA_MOBILE)
        second ?: throw FbException(NOT_AVAILABLE)
    }

    private fun mobileVariant(url: String): String = runCatching {
        val u = Uri.parse(url)
        val h = u.host ?: return url
        if (h == "www.facebook.com" || h == "facebook.com" || h == "web.facebook.com") {
            u.buildUpon().authority("m.facebook.com").build().toString()
        } else url
    }.getOrDefault(url)

    private fun tryFetch(url: String, ua: String): Info? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Sec-Fetch-Mode", "navigate")
            .build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val html = readUseful(r.body ?: return null)
            val finalUrl = r.request.url.toString()
            val hd = hdKeys.firstNotNullOfOrNull { find(html, it) }
            val sd = sdKeys.firstNotNullOfOrNull { find(html, it) }
            if (hd == null && sd == null) return null
            val title = meta(html, "og:title")?.takeIf { it.isNotBlank() && !it.equals("Facebook", true) }
                ?: "Facebook ${idOf(finalUrl) ?: idOf(url) ?: "video"}"
            val thumb = meta(html, "og:image")?.takeIf { it.startsWith("http") }
            val dur = Regex("\"playable_duration_in_ms\"\\s*:\\s*(\\d+)").find(html)
                ?.groupValues?.get(1)?.toLongOrNull()?.div(1000) ?: 0L
            return Info(finalUrl, title, thumb, hd, sd, dur)
        }
    }

    /** Reads the page in chunks and stops soon after the video links appear (the page is often 2 MB or more). */
    private fun readUseful(body: ResponseBody): String {
        val sb = StringBuilder()
        val source = body.source()
        val buf = Buffer()
        var foundAt = -1
        val keys = (hdKeys + sdKeys).map { "\"$it\"" }
        while (sb.length < 3_000_000) {
            if (source.read(buf, 65536L) < 0L) break
            sb.append(buf.readUtf8())
            if (foundAt < 0 && keys.any { sb.contains(it) }) foundAt = sb.length
            if (foundAt >= 0 && sb.length - foundAt > 120_000) break
        }
        return sb.toString()
    }

    private fun idOf(url: String): String? =
        runCatching { Uri.parse(url).getQueryParameter("v") }.getOrNull()
            ?: Regex("(\\d{8,})").find(url)?.value

    private fun find(html: String, key: String): String? {
        val re = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val m = re.find(html) ?: return null
        return unescape(m.groupValues[1]).takeIf { it.startsWith("http") }
    }

    private fun meta(html: String, prop: String): String? {
        val p = Regex.escape(prop)
        val a = Regex("property=[\"']$p[\"']\\s+content=[\"']([^\"']*)[\"']").find(html)
        val b = Regex("content=[\"']([^\"']*)[\"']\\s+property=[\"']$p[\"']").find(html)
        val raw = (a ?: b)?.groupValues?.get(1) ?: return null
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    private fun unescape(s: String): String {
        var r = s.replace("\\/", "/")
        r = Regex("\\\\u([0-9a-fA-F]{4})").replace(r) { it.groupValues[1].toInt(16).toChar().toString() }
        return r.replace("&amp;", "&")
    }

    /** Size from a 1 byte range request, -1 if unknown. */
    suspend fun probeSize(url: String, ua: String = Net.UA_CHROME): Long = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", ua).header("Range", "bytes=0-0").build()
            Net.client.newCall(req).execute().use { r ->
                when (r.code) {
                    206 -> r.header("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: -1L
                    200 -> r.header("Content-Length")?.toLongOrNull() ?: -1L
                    else -> -1L
                }
            }
        } catch (e: IOException) {
            -1L
        }
    }
}
