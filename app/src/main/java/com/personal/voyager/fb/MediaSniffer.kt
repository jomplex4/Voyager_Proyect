package com.personal.voyager.fb

import android.net.Uri
import android.util.Base64

/**
 * Facebook's player asks for its video/audio files piece by piece. Two sources of addresses are kept:
 *  - [note]: files the player really requested (what is playing now);
 *  - [noteAvailable]: addresses found in the page data (all the qualities of a video).
 * Every file carries the id of its video (inside the address), so the qualities of the video being watched
 * can be told apart from those of other reels loaded around it.
 */
object MediaSniffer {
    class Opt(val height: Int, val url: String, val muxed: Boolean)
    class Picked(val options: List<Opt>, val audio: String?)

    private class E(
        val key: String, var url: String, val kind: Int, val height: Int, val asset: String?,
        val tag: String, var count: Int, var time: Long,
    )

    private val seen = LinkedHashMap<String, E>()
    private val avail = LinkedHashMap<String, E>()

    private fun parse(raw: String, at: Long, requested: Boolean) {
        if (!raw.contains(".mp4") || !(raw.contains("fbcdn") || raw.contains("facebook"))) return
        val u = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        val host = u.host ?: return
        if (!host.contains("fbcdn") && !host.contains("facebook")) return
        val tag = u.getQueryParameter("efg")?.let {
            runCatching { String(Base64.decode(it.replace('-', '+').replace('_', '/').trimEnd('='), Base64.DEFAULT)) }.getOrNull()
        }?.lowercase() ?: ""
        val kind = when {
            tag.contains("audio") -> 1
            tag.contains("progressive") -> 2
            tag.contains("dash") || tag.contains("video") -> 0
            else -> 2
        }
        val height = Regex("(\\d{3,4})p").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val asset = Regex("(?:xpv_asset_id|video_id)\\D{1,4}(\\d{5,})").find(tag)?.groupValues?.get(1)
        // The player asks for pieces (bytestart/byteend); without them the address is the whole file.
        var clean = raw.replace(Regex("[?&](bytestart|byteend)=\\d+"), "")
        if (!clean.contains('?') && clean.contains('&')) clean = clean.replaceFirst('&', '?')
        val key = host + (u.path ?: "")
        synchronized(this) {
            val map = if (requested) seen else avail
            val e = map[key]
            if (e == null) map[key] = E(key, clean, kind, height, asset, tag, if (requested) 1 else 0, at)
            else {
                if (requested) e.count++
                if (requested) e.time = at
                e.url = clean
            }
            val cut = at - 5 * 60_000
            map.entries.removeAll { it.value.time < cut }
            while (map.size > 400) map.remove(map.keys.first())
        }
    }

    /** [raw] is a URL the player requested. */
    fun note(raw: String, at: Long = System.currentTimeMillis()) = parse(raw, at, true)

    /** Addresses the page loaded (one per line, oldest first). */
    fun noteAll(list: String) {
        val lines = list.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var t = System.currentTimeMillis() - lines.size
        for (l in lines) parse(l, t++, true)
    }

    /** Addresses found in the data of the page (all qualities). */
    fun noteAvailable(list: String) {
        val now = System.currentTimeMillis()
        list.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { parse(it, now, false) }
    }

    private fun codecOk(e: E) = !(e.tag.contains("vp9") || e.tag.contains("av1") || e.tag.contains("hevc") || e.tag.contains("h265"))

    /**
     * The video being watched. [wantedId] is the id taken from its page address when there is one; otherwise the video
     * of the most recent request. All qualities of that video are offered, one H.264 file per quality.
     */
    @Synchronized
    fun pick(wantedId: String?): Picked? {
        val now = System.currentTimeMillis()
        val merged = LinkedHashMap<String, E>()
        avail.forEach { merged[it.key] = it.value }
        seen.forEach { if (now - it.value.time < 5 * 60_000) merged[it.key] = it.value }
        val recent = seen.values.filter { now - it.time < 300_000 }

        val asset = when {
            wantedId != null && merged.values.any { it.asset == wantedId } -> wantedId
            else -> recent.filter { it.kind != 1 }.maxByOrNull { it.time }?.asset
        }
        val pool = if (asset != null) merged.values.filter { it.asset == asset }
        else {
            // No video id inside the addresses: the video most requested among the newest requests.
            val c = recent.filter { it.kind != 1 }
            val newest = c.maxOfOrNull { it.time } ?: return null
            val v = c.filter { newest - it.time < 25_000 }.maxWithOrNull(compareBy<E>({ it.count }, { it.time })) ?: return null
            listOf(v) + recent.filter { it.kind == 1 }.sortedByDescending { it.time }.take(1)
        }
        val vids = pool.filter { it.kind != 1 }
        if (vids.isEmpty()) return null
        val usable = if (vids.any { codecOk(it) }) vids.filter { codecOk(it) } else vids
        val options = usable.groupBy { it.height }.map { (h, list) ->
            val best = list.sortedWith(compareBy<E>({ -it.count }, { -it.time })).first()
            Opt(h, best.url, best.kind == 2)
        }.sortedByDescending { it.height }
        val audio = pool.filter { it.kind == 1 }.sortedWith(compareBy<E>({ -it.count }, { -it.time })).firstOrNull()?.url
        return Picked(options, audio)
    }
}
