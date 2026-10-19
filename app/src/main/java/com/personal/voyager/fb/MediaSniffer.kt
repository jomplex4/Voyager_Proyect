package com.personal.voyager.fb

import android.net.Uri
import android.util.Base64

/**
 * Remembers the video/audio files the Facebook player itself asks for while a reel plays (the same trick download
 * extensions use). It does not depend on how Facebook builds its page, only on the player asking for .mp4 files.
 */
object MediaSniffer {
    class Picked(val video: String, val audio: String?, val height: Int)

    private class E(var url: String, val kind: Int, val height: Int, var count: Int, var time: Long)

    private val map = LinkedHashMap<String, E>()

    /** [raw] is any URL the page loaded; only Facebook's .mp4 media files are kept (kind 0 video, 1 audio, 2 both). */
    fun note(raw: String, at: Long = System.currentTimeMillis()) {
        if (!raw.contains(".mp4") || !(raw.contains("fbcdn") || raw.contains("facebook"))) return
        val u = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        val host = u.host ?: return
        if (!host.contains("fbcdn") && !host.contains("facebook")) return
        val tag = u.getQueryParameter("efg")?.let {
            runCatching { String(Base64.decode(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)) }.getOrNull()
        }?.lowercase() ?: ""
        val kind = when {
            tag.contains("audio") -> 1
            tag.contains("progressive") -> 2
            tag.contains("dash") || tag.contains("video") -> 0
            else -> 2
        }
        val height = Regex("(\\d{3,4})p").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        // The player asks for pieces (bytestart/byteend); without them the address is the whole file.
        var clean = raw.replace(Regex("[?&](bytestart|byteend)=\\d+"), "")
        if (!clean.contains('?') && clean.contains('&')) clean = clean.replaceFirst('&', '?')
        val key = host + (u.path ?: "")
        synchronized(this) {
            val e = map[key]
            if (e == null) map[key] = E(clean, kind, height, 1, at) else {
                e.count++
                e.time = at
                e.url = clean
            }
            val cut = at - 5 * 60_000
            map.entries.removeAll { it.value.time < cut }
            while (map.size > 40) map.remove(map.keys.first())
        }
    }

    /** A list of addresses (one per line, oldest first) collected by the page script. */
    fun noteAll(list: String) {
        val lines = list.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var t = System.currentTimeMillis() - lines.size
        for (l in lines) note(l, t++)
    }

    /** The video (and audio) that is playing now: the file asked for most often among the most recent ones. */
    @Synchronized
    fun pick(): Picked? {
        val now = System.currentTimeMillis()
        val recent = map.values.filter { now - it.time < 120_000 }
        fun best(kinds: Set<Int>): E? {
            val c = recent.filter { it.kind in kinds }
            val newest = c.maxOfOrNull { it.time } ?: return null
            return c.filter { newest - it.time < 25_000 }.maxWithOrNull(compareBy<E>({ it.count }, { it.time }))
        }
        val v = best(setOf(0, 2)) ?: return null
        val a = if (v.kind == 0) best(setOf(1)) else null
        return Picked(v.url, a?.url, v.height)
    }
}
