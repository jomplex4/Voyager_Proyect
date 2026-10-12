package com.personal.voyager.dl

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import net.sourceforge.lame.Mp3Encoder
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteOrder

/** MediaCodec decode to PCM, then LAME (pure Java port) encode at constant bitrate. Blocking. */
object Mp3 {
    // 1 best/slowest ... 9 fastest. At 160K and above the difference is inaudible, so use the fast setting there.
    private fun quality(kbps: Int) = if (kbps >= 160) 7 else 5

    fun encode(
        srcPath: String,
        outPath: String,
        kbps: Int,
        header: ByteArray?,
        check: () -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val ex = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: Mp3Encoder? = null
        try {
            ex.setDataSource(srcPath)
            var track = -1
            for (i in 0 until ex.trackCount) {
                val m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    track = i
                    break
                }
            }
            if (track < 0) throw IOException("The downloaded file has no audio track")
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: throw IOException("Unknown audio format")
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L

            val dec = MediaCodec.createDecoderByType(mime)
            decoder = dec
            dec.configure(fmt, null, null, 0)
            dec.start()

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var channels = 0
            var floatPcm = false
            var shorts = ShortArray(16384)
            var mp3 = ByteArray(32768)
            var tick = 0
            var lastOut = System.nanoTime()

            BufferedOutputStream(FileOutputStream(outPath), 128 * 1024).use { out ->
                if (header != null) out.write(header)
                while (!outDone) {
                    if (++tick % 16 == 0) check()
                    if (!inDone) {
                        val ii = dec.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val ib = dec.getInputBuffer(ii) ?: throw IOException("Decoder input unavailable")
                            val n = ex.readSampleData(ib, 0)
                            if (n < 0) {
                                dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inDone = true
                            } else {
                                dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val oi = dec.dequeueOutputBuffer(info, 10_000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || (oi >= 0 && encoder == null)) {
                        val f = dec.outputFormat
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        if (encoder == null) encoder = Mp3Encoder(minOf(channels, 2), rate, kbps, quality(kbps))
                    }
                    if (oi < 0 && inDone && System.nanoTime() - lastOut > 20_000_000_000L) {
                        throw IOException("The audio decoder stopped responding")
                    }
                    if (oi >= 0) {
                        lastOut = System.nanoTime()
                        val enc = encoder ?: throw IOException("Could not decode the audio")
                        val ob = dec.getOutputBuffer(oi) ?: throw IOException("Decoder output unavailable")
                        if (info.size > 0) {
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            ob.order(ByteOrder.LITTLE_ENDIAN)
                            val perSample = if (floatPcm) 4 else 2
                            val frames = (info.size / perSample) / channels
                            val outCh = minOf(channels, 2)
                            val need = frames * outCh
                            if (shorts.size < need) shorts = ShortArray(need)
                            if (floatPcm) {
                                val fb = ob.asFloatBuffer()
                                for (fr in 0 until frames) {
                                    for (c in 0 until channels) {
                                        val v = fb.get()
                                        if (c < outCh) {
                                            shorts[fr * outCh + c] = (v * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                                        }
                                    }
                                }
                            } else if (channels == outCh) {
                                ob.asShortBuffer().get(shorts, 0, need)
                            } else {
                                val sb = ob.asShortBuffer()
                                for (fr in 0 until frames) {
                                    for (c in 0 until channels) {
                                        val v = sb.get()
                                        if (c < outCh) shorts[fr * outCh + c] = v
                                    }
                                }
                            }
                            val maxOut = (1.25 * frames + 7200).toInt()
                            if (mp3.size < maxOut) mp3 = ByteArray(maxOut)
                            val written = enc.encode(shorts, 0, need, mp3)
                            if (written > 0) out.write(mp3, 0, written)
                            if (durUs > 0) onProgress((info.presentationTimeUs.toFloat() / durUs).coerceIn(0f, 1f))
                        }
                        dec.releaseOutputBuffer(oi, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                    }
                }
                val enc = encoder ?: throw IOException("Could not decode the audio")
                if (mp3.size < 16384) mp3 = ByteArray(16384)
                val tail = enc.finish(mp3)
                if (tail > 0) out.write(mp3, 0, tail)
            }
        } finally {
            runCatching { encoder?.close() }
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            ex.release()
        }
    }
}
