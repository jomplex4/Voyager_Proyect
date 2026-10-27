package com.personal.voyager.dl

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.IOException
import java.nio.ByteBuffer

/** Muxing with the Android framework only (no FFmpeg). All functions are blocking. */
object Mux {

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun maxInput(f: MediaFormat): Int =
        if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0

    private fun pump(
        ex: MediaExtractor,
        outTrack: Int,
        muxer: MediaMuxer,
        buf: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ): Boolean {
        buf.clear()
        val size = ex.readSampleData(buf, 0)
        if (size < 0) return false
        val flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        info.set(0, size, maxOf(0L, ex.sampleTime), flags) // some files start slightly before zero
        muxer.writeSampleData(outTrack, buf, info)
        ex.advance()
        return true
    }

    /** Video-only + audio-only into MP4 (AVC+AAC) or WebM (VP9+Opus). */
    fun mergeVideoAudio(
        videoPath: String,
        audioPath: String,
        outPath: String,
        webm: Boolean,
        check: () -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val vEx = MediaExtractor()
        val aEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            vEx.setDataSource(videoPath)
            aEx.setDataSource(audioPath)
            val vt = firstTrack(vEx, "video/")
            val at = firstTrack(aEx, "audio/")
            if (vt < 0 || at < 0) throw IOException("The downloaded streams have no video or audio track")
            vEx.selectTrack(vt)
            aEx.selectTrack(at)
            val vf = vEx.getTrackFormat(vt)
            val af = aEx.getTrackFormat(at)
            val durUs = if (vf.containsKey(MediaFormat.KEY_DURATION)) vf.getLong(MediaFormat.KEY_DURATION) else 0L

            muxer = MediaMuxer(
                outPath,
                if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            val vOut = muxer.addTrack(vf)
            val aOut = muxer.addTrack(af)
            muxer.start()
            started = true

            val size = maxOf(4 * 1024 * 1024, maxInput(vf), maxInput(af))
            val buf = ByteBuffer.allocate(size)
            val info = MediaCodec.BufferInfo()
            var vDone = false
            var aDone = false
            var n = 0
            while (true) {
                // The end of a stream is "no track left", never a negative time: Facebook's sound starts a few
                // milliseconds before zero and was being dropped completely.
                val vTime = if (vDone) Long.MAX_VALUE else if (vEx.sampleTrackIndex < 0) {
                    vDone = true
                    Long.MAX_VALUE
                } else vEx.sampleTime
                val aTime = if (aDone) Long.MAX_VALUE else if (aEx.sampleTrackIndex < 0) {
                    aDone = true
                    Long.MAX_VALUE
                } else aEx.sampleTime
                if (vDone && aDone) break
                if (vTime <= aTime) {
                    if (!pump(vEx, vOut, muxer, buf, info)) vDone = true
                } else {
                    if (!pump(aEx, aOut, muxer, buf, info)) aDone = true
                }
                if (++n % 400 == 0) {
                    check()
                    if (durUs > 0) onProgress((minOf(vTime, aTime).toFloat() / durUs).coerceIn(0f, 1f))
                }
            }
            muxer.stop()
            started = false
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            vEx.release()
            aEx.release()
        }
    }

    /** True for streaming-layout MP4 (in fragments). A regular MP4 is kept exactly as Facebook delivered it. */
    fun isFragmented(path: String): Boolean = runCatching {
        java.io.RandomAccessFile(path, "r").use { f ->
            var pos = 0L
            val len = f.length()
            var n = 0
            var result = false
            while (pos + 8 <= len && n++ < 40) {
                f.seek(pos)
                val size32 = f.readInt().toLong() and 0xFFFFFFFFL
                val b = ByteArray(4)
                f.readFully(b)
                val type = String(b, Charsets.ISO_8859_1)
                if (type == "moof" || type == "sidx") { result = true; break }
                if (type == "mdat") break
                val size = when (size32) { 1L -> f.readLong(); 0L -> len - pos; else -> size32 }
                if (size < 8) { result = true; break }
                pos += size
            }
            result
        }
    }.getOrDefault(true)

    /** Throws when the file has no readable video (or no sound when it must have it): a clear error beats a broken file in the list. */
    fun validateVideo(path: String, needAudio: Boolean = false) {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            val hasVideo = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val dur = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (hasVideo != "yes" || dur <= 0) throw IOException("The video file could not be read, try another quality")
            val ex = MediaExtractor()
            try {
                ex.setDataSource(path)
                var videoChecked = false
                var hasAudio = false
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) hasAudio = true
                    if (!mime.startsWith("video/") || videoChecked) continue
                    videoChecked = true
                    val probe = MediaFormat.createVideoFormat(mime, f.getInteger(MediaFormat.KEY_WIDTH), f.getInteger(MediaFormat.KEY_HEIGHT))
                    if (android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).findDecoderForFormat(probe) == null) {
                        throw IOException("This quality is saved in a format ($mime) that this phone cannot play. Choose another quality")
                    }
                }
                if (needAudio && !hasAudio) throw IOException("The video was saved without sound. Choose another quality")
            } finally {
                ex.release()
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("The video file could not be read, try another quality")
        } finally {
            runCatching { r.release() }
        }
    }

    /** Copies every video/audio track of a (fragmented) MP4 into a normal MP4 that any player opens. */
    fun remuxToMp4(inPath: String, outPath: String, check: () -> Unit, onProgress: (Float) -> Unit) {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            ex.setDataSource(inPath)
            muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val map = HashMap<Int, Int>()
            var maxIn = 1 shl 20
            var durUs = 0L
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    ex.selectTrack(i)
                    map[i] = muxer.addTrack(f)
                    if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxIn = maxOf(maxIn, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    if (f.containsKey(MediaFormat.KEY_DURATION)) durUs = maxOf(durUs, f.getLong(MediaFormat.KEY_DURATION))
                }
            }
            if (map.isEmpty()) throw IOException("The downloaded file has no video or audio track")
            muxer.start()
            started = true
            val buf = ByteBuffer.allocate(maxIn)
            val info = MediaCodec.BufferInfo()
            var n = 0
            while (true) {
                if ((n++ and 63) == 0) check()
                val track = ex.sampleTrackIndex
                if (track < 0) break
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, maxOf(0L, ex.sampleTime), flags)
                muxer.writeSampleData(map[track] ?: break, buf, info)
                if (durUs > 0 && n % 200 == 0) onProgress((ex.sampleTime.toFloat() / durUs).coerceIn(0f, 1f))
                ex.advance()
            }
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            ex.release()
        }
    }

    fun remuxAudioToM4a(inPath: String, outPath: String, check: () -> Unit, onProgress: (Float) -> Unit) {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            ex.setDataSource(inPath)
            val t = firstTrack(ex, "audio/")
            if (t < 0) throw IOException("The downloaded file has no audio track")
            ex.selectTrack(t)
            val f = ex.getTrackFormat(t)
            val durUs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val out = muxer.addTrack(f)
            muxer.start()
            started = true
            val buf = ByteBuffer.allocate(maxOf(1024 * 1024, maxInput(f)))
            val info = MediaCodec.BufferInfo()
            var n = 0
            while (pump(ex, out, muxer, buf, info)) {
                if (++n % 400 == 0) {
                    check()
                    if (durUs > 0) onProgress((ex.sampleTime.toFloat() / durUs).coerceIn(0f, 1f))
                }
            }
            muxer.stop()
            started = false
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            ex.release()
        }
    }
}
