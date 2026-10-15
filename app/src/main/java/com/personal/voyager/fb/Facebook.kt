package com.personal.voyager.fb

import android.net.Uri
import android.text.Html
import com.personal.voyager.core.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException
import java.net.URLDecoder

/**
 * Facebook videos. Public ones work without an account; private or friends-only ones use the account the user
 * logged in with (cookie from the in-app WebView). Isolated here because Facebook changes its pages often.
 */
object Facebook {
    class Info(
        val pageUrl: String,
        val title: String,
        val thumb: String?,
        val hdUrl: String?,
        val sdUrl: String?,
        val durationSec: Long,
        val dashVideos: List<Rep> = emptyList(),
        val dashAudio: Rep? = null,
    )

    /** One stream of Facebook's DASH manifest (separate video or audio file). */
    class Rep(val width: Int, val height: Int, val bandwidth: Long, val url: String, val codecs: String)

    private val STANDARD = intArrayOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)

    /** Quality label like YouTube shows it (a 1920x804 wide video is 1080p). */
    private fun label(w: Int, h: Int): Int {
        val eff = if (w <= 0) h else if (w >= h) maxOf(h, Math.round(w * 9f / 16f)) else w
        val best = STANDARD.minByOrNull { Math.abs(it - eff) } ?: eff
        return if (Math.abs(best - eff) <= best * 0.08) best else eff
    }

    /** Best H.264 stream for every quality, ascending: (label, stream). */
    fun bestReps(info: Info): List<Pair<Int, Rep>> =
        info.dashVideos.groupBy { label(it.width, it.height) }.mapNotNull { (h, list) ->
            val pick = list.filter { it.codecs.startsWith("avc1") }.maxByOrNull { it.bandwidth } ?: return@mapNotNull null
            h to pick
        }.sortedBy { it.first }

    private fun parseDash(html: String): Triple<List<Rep>, Rep?, Long> {
        val m = Regex("\"dash_manifest\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(html)
            ?: return Triple(emptyList(), null, 0L)
        val xml = unescape(m.groupValues[1]).replace("\\\"", "\"").replace("\\n", "\n")
        val videos = ArrayList<Rep>()
        var audio: Rep? = null
        val attr = Regex("(\\w+)=\"([^\"]*)\"")
        for (r in Regex("<Representation\\b([^>]*)>(.*?)</Representation>", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val a = attr.findAll(r.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val url = Regex("<BaseURL>(.*?)</BaseURL>", RegexOption.DOT_MATCHES_ALL).find(r.groupValues[2])
                ?.groupValues?.get(1)?.trim()?.replace("&amp;", "&") ?: continue
            if (!url.startsWith("http")) continue
            val bw = a["bandwidth"]?.toLongOrNull() ?: 0L
            val codecs = a["codecs"] ?: ""
            val h = a["height"]?.toIntOrNull()
            if (h != null) {
                videos.add(Rep(a["width"]?.toIntOrNull() ?: 0, h, bw, url, codecs))
            } else if (audio == null || bw > audio.bandwidth) {
                audio = Rep(0, 0, bw, url, codecs)
            }
        }
        val d = Regex("mediaPresentationDuration=\"PT(?:(\\d+)H)?(?:(\\d+)M)?(?:([\\d.]+)S)?\"").find(xml)
        val secs = if (d == null) 0L else
            (d.groupValues[1].toLongOrNull() ?: 0L) * 3600 + (d.groupValues[2].toLongOrNull() ?: 0L) * 60 +
                (d.groupValues[3].toDoubleOrNull()?.toLong() ?: 0L)
        return Triple(videos, audio, secs)
    }

    class FbException(msg: String, val loginNeeded: Boolean = false) : Exception(msg)

    const val NOT_AVAILABLE = "This Facebook video is not available for your account, or Facebook changed its page."
    const val LOGIN_NEEDED = "This Facebook video is private or needs an account."

    private val hdKeys = listOf(
        "playable_url_quality_hd", "browser_native_hd_url", "hd_src_no_ratelimit", "hd_src", "hd_url", "video_hd_url",
    )
    private val sdKeys = listOf(
        "playable_url", "browser_native_sd_url", "sd_src_no_ratelimit", "sd_src", "sd_url", "video_sd_url",
    )

    /** The cookie is only ever sent to facebook.com hosts, even if a redirect points somewhere else. */
    private val client: OkHttpClient by lazy {
        Net.client.newBuilder().addNetworkInterceptor { chain ->
            val req = chain.request()
            if (req.url.host.endsWith("facebook.com")) chain.proceed(req)
            else chain.proceed(req.newBuilder().removeHeader("Cookie").build())
        }.build()
    }

    suspend fun fetch(inputUrl: String): Info = withContext(Dispatchers.IO) {
        parsePage(inputUrl, Net.UA_CHROME)?.let { return@withContext it }
        parsePage(mobileVariant(inputUrl), Net.UA_MOBILE)?.let { return@withContext it }
        if (FbSession.loggedIn()) {
            parseMbasic(inputUrl)?.let { return@withContext it }
            throw FbException(NOT_AVAILABLE)
        }
        throw FbException(LOGIN_NEEDED, loginNeeded = true)
    }

    private fun mobileVariant(url: String): String = hostSwap(url, "m.facebook.com", setOf("www.facebook.com", "facebook.com", "web.facebook.com"))

    private fun hostSwap(url: String, newHost: String, from: Set<String>): String = runCatching {
        val u = Uri.parse(url)
        if ((u.host ?: "") in from) u.buildUpon().authority(newHost).build().toString() else url
    }.getOrDefault(url)

    /** Returns (html, finalUrl) or null. */
    private fun load(url: String, ua: String): Pair<String, String>? {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Sec-Fetch-Mode", "navigate")
        val host = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
        if (host.endsWith("facebook.com") || host == "fb.watch") FbSession.cookie()?.let { b.header("Cookie", it) }
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val html = readUseful(r.body ?: return null)
            return html to r.request.url.toString()
        }
    }

    private fun parsePage(url: String, ua: String): Info? {
        val (html, finalUrl) = load(url, ua) ?: return null
        val hd = hdKeys.firstNotNullOfOrNull { find(html, it) }
        val sd = sdKeys.firstNotNullOfOrNull { find(html, it) }
        val (dashV, dashA, dashSecs) = parseDash(html)
        if (hd == null && sd == null && dashV.isEmpty()) return null
        val title = meta(html, "og:title")?.replace(Regex("^[\\d.,]+[KMB]? views\\s*·\\s*[\\d.,]+[KMB]? reactions\\s*\\|\\s*"), "")?.takeIf { it.isNotBlank() && !it.equals("Facebook", true) }
            ?: "Facebook ${idOf(finalUrl) ?: idOf(url) ?: "video"}"
        val thumb = meta(html, "og:image")?.takeIf { it.startsWith("http") }
        val dur = Regex("\"playable_duration_in_ms\"\\s*:\\s*(\\d+)").find(html)
            ?.groupValues?.get(1)?.toLongOrNull()?.div(1000) ?: 0L
        return Info(finalUrl, title, thumb, hd, sd, if (dur > 0) dur else dashSecs, dashV, dashA)
    }

    /** Last resort for logged in accounts: the plain mobile site links the video file directly. */
    private fun parseMbasic(url: String): Info? {
        val u = hostSwap(url, "mbasic.facebook.com", setOf("www.facebook.com", "facebook.com", "web.facebook.com", "m.facebook.com"))
        if (u == url) return null
        val (html, finalUrl) = load(u, Net.UA_MOBILE) ?: return null
        val m = Regex("video_redirect/\\?src=([^\"&]+)").find(html) ?: return null
        val src = runCatching { URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrNull() ?: return null
        if (!src.startsWith("http")) return null
        val title = meta(html, "og:title")?.takeIf { it.isNotBlank() } ?: "Facebook ${idOf(finalUrl) ?: "video"}"
        return Info(finalUrl, title, meta(html, "og:image"), null, src, 0L)
    }

    /** Reads the page in chunks and stops soon after the video links appear (the page is often 2 MB or more). */
    private fun readUseful(body: ResponseBody): String {
        val sb = StringBuilder()
        val source = body.source()
        val buf = Buffer()
        var foundAt = -1
        val keys = (hdKeys + sdKeys).map { "\"$it\"" }
        while (sb.length < 4_000_000) {
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
