package com.personal.voyager.fb

import com.personal.voyager.core.Net
import com.personal.voyager.dl.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Looks inside a file instead of guessing from its address: the first bytes of an MP4 say whether it holds picture,
 * sound or both, its size and its format.
 */
object MediaProbe {
    class Info(val video: Boolean, val audio: Boolean, val width: Int, val height: Int, val codec: String)

    suspend fun probe(url: String): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).header("Range", "bytes=0-524287").header("User-Agent", Net.UA_CHROME).build()
            Net.client.newCall(req).execute().use { r ->
                val body = r.body
                if (!r.isSuccessful || body == null) return@use null
                val buf = ByteArray(524288)
                var n = 0
                val ins = body.byteStream()
                while (n < buf.size) {
                    val k = ins.read(buf, n, buf.size - n)
                    if (k < 0) break
                    n += k
                }
                parse(buf, n)
            }
        }.getOrNull()
    }

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 255) shl 24) or ((b[o + 1].toLong() and 255) shl 16) or ((b[o + 2].toLong() and 255) shl 8) or (b[o + 3].toLong() and 255)

    private fun type(b: ByteArray, o: Int) = String(b, o, 4, Charsets.ISO_8859_1)

    /** Boxes found in [start, end): (type, start of its content, end of the box). */
    private fun boxes(b: ByteArray, start: Int, end: Int): List<Triple<String, Int, Int>> {
        val out = ArrayList<Triple<String, Int, Int>>()
        var p = start
        while (p + 8 <= end) {
            var size = u32(b, p)
            var hdr = 8
            if (size == 1L && p + 16 <= end) {
                size = (u32(b, p + 8) shl 32) or u32(b, p + 12)
                hdr = 16
            } else if (size == 0L) size = (end - p).toLong()
            if (size < hdr) break
            val e = minOf(p + size, end.toLong()).toInt()
            out.add(Triple(type(b, p + 4), p + hdr, e))
            if (p + size >= end) break
            p += size.toInt()
        }
        return out
    }

    private fun parse(b: ByteArray, len: Int): Info? {
        val moov = boxes(b, 0, len).firstOrNull { it.first == "moov" } ?: return null
        var video = false
        var audio = false
        var w = 0
        var h = 0
        var codec = ""
        for (trak in boxes(b, moov.second, moov.third).filter { it.first == "trak" }) {
            val kids = boxes(b, trak.second, trak.third)
            var handler = ""
            var fourcc = ""
            var tw = 0
            var th = 0
            kids.firstOrNull { it.first == "tkhd" }?.let { t ->
                val v = b[t.second].toInt()
                val o = t.second + if (v == 1) 88 else 76
                if (o + 8 <= t.third) {
                    tw = (u32(b, o) shr 16).toInt()
                    th = (u32(b, o + 4) shr 16).toInt()
                }
            }
            kids.firstOrNull { it.first == "mdia" }?.let { mdia ->
                val mk = boxes(b, mdia.second, mdia.third)
                mk.firstOrNull { it.first == "hdlr" }?.let { if (it.second + 12 <= it.third) handler = type(b, it.second + 8) }
                mk.firstOrNull { it.first == "minf" }?.let { minf ->
                    boxes(b, minf.second, minf.third).firstOrNull { it.first == "stbl" }?.let { stbl ->
                        boxes(b, stbl.second, stbl.third).firstOrNull { it.first == "stsd" }?.let { stsd ->
                            if (stsd.second + 16 <= stsd.third) fourcc = type(b, stsd.second + 12)
                        }
                    }
                }
            }
            if (handler == "vide") {
                video = true
                w = tw
                h = th
                codec = when (fourcc) {
                    "avc1", "avc3" -> "H.264"
                    "hvc1", "hev1" -> "HEVC"
                    "av01" -> "AV1"
                    "vp09" -> "VP9"
                    else -> fourcc
                }
            } else if (handler == "soun") audio = true
        }
        return if (video || audio) Info(video, audio, w, h, codec) else null
    }

    /** What the probed files really are, turned into the options of the list (H.264 first, complete MP4s first). */
    fun build(items: List<Pair<MediaSniffer.Cand, Info?>>): MediaSniffer.Picked? {
        val ok = items.mapNotNull { (c, i) -> if (i == null) null else c to i }
        val vids = ok.filter { it.second.video }
        if (vids.isEmpty()) return null
        val auds = ok.filter { it.second.audio && !it.second.video }
        val h264 = vids.filter { it.second.codec == "H.264" }
        val usable = if (h264.isNotEmpty()) h264 else vids
        val options = usable.groupBy { (c, i) -> if (i.height > 0) i.height else c.height }.map { (h, list) ->
            val best = list.sortedWith(compareBy({ if (it.second.audio) 0 else 1 }, { -it.first.count })).first()
            MediaSniffer.Opt(h, best.first.url, best.second.audio, best.second.codec)
        }.sortedWith(compareBy({ -it.height }, { if (it.muxed) 0 else 1 }))
        val audio = auds.sortedBy { -it.first.count }.firstOrNull()?.first?.url
        return MediaSniffer.Picked(options, audio)
    }

    /** Text for facebook-report.txt: what was found and what each file really is. */
    fun report(title: String, items: List<Pair<MediaSniffer.Cand, Info?>>, picked: MediaSniffer.Picked?): String = buildString {
        append("=== ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        append("  ").append(title).append('\n')
        for ((c, i) in items) {
            append("- guess kind=${c.kind} h=${c.height} asset=${c.asset} requests=${c.count} tag=${c.tag.take(150)}\n")
            append("    file=...${c.url.substringBefore('?').takeLast(45)}\n")
            append("    inside: ").append(
                if (i == null) "could not be read" else "video=${i.video} audio=${i.audio} ${i.width}x${i.height} ${i.codec}"
            ).append('\n')
        }
        append("chosen: ")
        if (picked == null) append("nothing") else {
            append(picked.options.joinToString { "${it.height}p${if (it.muxed) "+sound" else ""}${if (it.codec.isNotEmpty()) "/" + it.codec else ""}" })
            append("  audio=").append(picked.audio != null)
        }
        append('\n')
    }

    fun writeReport(text: String) {
        runCatching {
            Storage.prepare()
            val f = File(Storage.root(), "facebook-report.txt")
            if (f.exists() && f.length() > 120_000) f.delete()
            f.appendText(text + "\n")
        }
    }
}
