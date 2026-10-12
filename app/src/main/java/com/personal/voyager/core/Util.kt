package com.personal.voyager.core

import android.net.Uri
import java.util.Locale

object Fmt {
    fun size(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun duration(sec: Long): String {
        if (sec <= 0) return "0:00"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun compact(n: Long): String = when {
        n >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", n / 1e9)
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1e3)
        else -> n.toString()
    }.replace(".0", "")

    fun views(n: Long): String = if (n < 0) "" else "${compact(n)} views"

    fun speed(bps: Long): String = when {
        bps >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bps / 1048576.0)
        bps >= 1024 -> String.format(Locale.US, "%d KB/s", bps / 1024)
        else -> "$bps B/s"
    }
}

object Names {
    private val bad = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    fun clean(s: String): String =
        s.replace(bad, " ").replace(Regex("\\s+"), " ").trim().trim('.').ifEmpty { "video" }

    /** "<prefix><Title> (<TAG>)" with the whole file name (plus extension) kept under 100 characters. */
    fun base(prefix: String, title: String, tag: String): String {
        val suffix = " ($tag)"
        val room = 100 - 5 - prefix.length - suffix.length // 5 = ".webm"
        var t = clean(title)
        if (t.length > room) t = t.take(room.coerceAtLeast(10)).trim()
        return "$prefix$t$suffix"
    }
}

object Links {
    private val urlRegex = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

    fun firstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']')
    }

    private fun host(url: String): String =
        runCatching { Uri.parse(url).host?.lowercase(Locale.US) ?: "" }.getOrDefault("")

    fun isYoutube(url: String): Boolean {
        val h = host(url)
        return h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com") || h == "youtube-nocookie.com"
    }

    fun isFacebook(url: String): Boolean {
        val h = host(url)
        return h == "fb.watch" || h == "fb.com" || h == "facebook.com" || h.endsWith(".facebook.com")
    }

    fun isSupported(url: String) = isYoutube(url) || isFacebook(url)

    fun domain(url: String): String = host(url).removePrefix("www.").removePrefix("m.")

    fun youtubeId(url: String): String? {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val h = host(url)
        if (h == "youtu.be") return u.lastPathSegment?.takeIf { it.length >= 8 }
        u.getQueryParameter("v")?.let { if (it.length >= 8) return it }
        val segs = u.pathSegments
        if (segs.size >= 2 && segs[0] in setOf("shorts", "embed", "live", "v")) return segs[1]
        return null
    }

    fun thumbForYoutube(url: String): String? =
        youtubeId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    fun short(url: String, max: Int = 30): String {
        val s = url.removePrefix("https://").removePrefix("http://")
        return if (s.length <= max) s else s.take(max - 1) + "…"
    }
}
