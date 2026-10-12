package com.personal.voyager.yt

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

enum class AudioKind(val tag: String, val label: String, val kbps: Int, val mp3: Boolean) {
    M4A_128("M4A_128K", "M4A 128K", 128, false),
    MP3_128("MP3_128K", "MP3 128K", 128, true),
    MP3_160("MP3_160K", "MP3 160K", 160, true),
    MP3_320("MP3_320K", "MP3 320K", 320, true);

    companion object {
        fun fromTag(tag: String): AudioKind = entries.firstOrNull { it.tag == tag } ?: MP3_128
    }
}

/** One selected way to get a video of a given height. */
class VideoSel(
    val height: Int,
    val fps: Int,
    val video: VideoStream?,
    val audio: AudioStream?,
    val muxed: VideoStream?,
    val webm: Boolean,
    val bytes: Long,
    val estimated: Boolean,
) {
    val label: String get() = "${height}p" + if (fps > 30) "$fps" else ""
}

class AudioOpt(val kind: AudioKind, val bytes: Long, val estimated: Boolean)

object Formats {
    private fun progressive(s: Stream) = s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && s.isUrl

    fun lengthOf(s: Stream): Long = runCatching { s.itagItem?.contentLength ?: -1L }.getOrDefault(-1L)

    private fun codecOf(v: VideoStream): String = (v.codec ?: "").lowercase()
    private fun isAvc(v: VideoStream) = v.format == MediaFormat.MPEG_4 && codecOf(v).startsWith("avc1")
    private fun isVp9(v: VideoStream) =
        v.format == MediaFormat.WEBM && (codecOf(v).startsWith("vp9") || codecOf(v).startsWith("vp09"))

    private fun best(list: List<VideoStream>): VideoStream? =
        list.maxWithOrNull(compareBy<VideoStream>({ it.fps }, { it.bitrate }))

    private fun originalFirst(list: List<AudioStream>): List<AudioStream> {
        val orig = list.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
        if (orig.isNotEmpty()) return orig
        val untyped = list.filter { it.audioTrackType == null }
        return if (untyped.isNotEmpty()) untyped else list
    }

    fun pickM4a(info: StreamInfo): AudioStream? =
        originalFirst(info.audioStreams.filter { progressive(it) && it.format == MediaFormat.M4A })
            .minByOrNull { abs(it.averageBitrate - 128) }

    fun pickOpus(info: StreamInfo): AudioStream? =
        originalFirst(info.audioStreams.filter { progressive(it) && it.format == MediaFormat.WEBMA_OPUS })
            .maxByOrNull { it.averageBitrate }

    fun availableHeights(info: StreamInfo): List<Int> {
        val h = HashSet<Int>()
        info.videoOnlyStreams.filter { progressive(it) && it.height > 0 }.forEach { h.add(it.height) }
        info.videoStreams.filter { progressive(it) && it.height > 0 }.forEach { h.add(it.height) }
        return h.sorted()
    }

    private fun estBytes(kbps: Int, durationSec: Long) = kbps.toLong().coerceAtLeast(0) * 125L * durationSec

    private fun build(info: StreamInfo, h: Int, allowWebm: Boolean): VideoSel? {
        val dur = info.duration
        val vOnly = info.videoOnlyStreams.filter { progressive(it) && it.height == h }
        val avc = best(vOnly.filter { isAvc(it) })
        val vp9 = best(vOnly.filter { isVp9(it) })

        fun merge(v: VideoStream, a: AudioStream?, webm: Boolean): VideoSel? {
            if (a == null) return null
            val vb = lengthOf(v)
            val ab = lengthOf(a)
            val est = vb <= 0 || ab <= 0
            val vBytes = if (vb > 0) vb else v.bitrate.toLong().coerceAtLeast(0) / 8 * dur
            val aBytes = if (ab > 0) ab else estBytes(a.averageBitrate, dur)
            return VideoSel(h, v.fps, v, a, null, webm, vBytes + aBytes, est)
        }

        if (h <= 1080 && avc != null) merge(avc, pickM4a(info), false)?.let { return it }
        if (allowWebm && vp9 != null) merge(vp9, pickOpus(info), true)?.let { return it }
        if (avc != null) merge(avc, pickM4a(info), false)?.let { return it }

        val muxed = info.videoStreams.filter { progressive(it) && it.height == h }.maxByOrNull { it.fps }
        if (muxed != null) {
            val b = lengthOf(muxed)
            val bytes = if (b > 0) b else estBytes(muxed.bitrate / 1000, dur)
            return VideoSel(h, muxed.fps, null, null, muxed, muxed.format == MediaFormat.WEBM, bytes, b <= 0)
        }
        return null
    }

    /** Every standard quality the video really has, 360p up to its maximum, ascending. */
    fun videoOptions(info: StreamInfo): List<VideoSel> =
        availableHeights(info).filter { it >= 360 }.mapNotNull { build(info, it, true) }

    /** The best way to get [requested]p, or the highest quality below it (never above the video's maximum). */
    fun selectVideo(info: StreamInfo, requested: Int, allowWebm: Boolean = true): VideoSel? {
        val heights = availableHeights(info)
        if (heights.isEmpty()) return null
        val cap = if (allowWebm) requested else minOf(requested, 1080)
        val start = heights.filter { it <= cap }.maxOrNull() ?: heights.minOrNull() ?: return null
        val order = listOf(start) + heights.filter { it < start }.sortedDescending() + heights.filter { it > start }.sorted()
        for (h in order) build(info, h, allowWebm)?.let { return it }
        return null
    }

    fun audioOptions(info: StreamInfo): List<AudioOpt> {
        val m4a = pickM4a(info)
        val opus = pickOpus(info)
        if (m4a == null && opus == null) return emptyList()
        val dur = info.duration
        val out = ArrayList<AudioOpt>()
        if (m4a != null) {
            val b = lengthOf(m4a)
            out.add(AudioOpt(AudioKind.M4A_128, if (b > 0) b else estBytes(m4a.averageBitrate, dur), b <= 0))
        }
        for (k in listOf(AudioKind.MP3_128, AudioKind.MP3_160, AudioKind.MP3_320)) {
            out.add(AudioOpt(k, estBytes(k.kbps, dur), true))
        }
        return out
    }
}
