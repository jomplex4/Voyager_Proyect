package com.personal.voyager.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.personal.voyager.core.Net
import com.personal.voyager.fb.Facebook
import com.personal.voyager.yt.Formats
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** One ExoPlayer for the whole process, shared by the preview screen and the media notification service. */
@OptIn(UnstableApi::class)
object PlayerHub {
    @Volatile
    private var player: ExoPlayer? = null
    private val rn = AtomicInteger(0)

    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        synchronized(this) {
            player?.let { return it }
            val p = ExoPlayer.Builder(ctx.applicationContext)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
                        .build()
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .setHandleAudioBecomingNoisy(true)
                .build()
            player = p
            return p
        }
    }

    fun peek(): ExoPlayer? = player

    fun stopPlayback() {
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    private fun factory(sampleUrl: String): DataSource.Factory {
        val props = HashMap<String, String>()
        if (YoutubeParsingHelper.isWebStreamingUrl(sampleUrl)) {
            props["Origin"] = "https://www.youtube.com"
            props["Referer"] = "https://www.youtube.com/"
        }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.UA_DESKTOP)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(props)
        return ResolvingDataSource.Factory(http) { spec ->
            val u = spec.uri.toString()
            if (u.contains("/videoplayback")) {
                spec.buildUpon()
                    .setUri(Uri.parse("$u&rn=${rn.incrementAndGet()}"))
                    .setHttpMethod(DataSpec.HTTP_METHOD_POST)
                    .setHttpBody(byteArrayOf(0x78, 0x00))
                    .build()
            } else spec
        }
    }

    private fun progressive(s: org.schabi.newpipe.extractor.stream.Stream) =
        s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && s.isUrl

    /** Default preview quality: 360p if the video has it, otherwise the closest one up to 480p. */
    fun defaultHeight(info: StreamInfo): Int {
        val hs = Formats.availableHeights(info)
        return hs.firstOrNull { it == 360 } ?: hs.filter { it <= 480 }.maxOrNull() ?: hs.minOrNull() ?: 360
    }

    /** Plays [height] (or the closest available one) from [startMs]. Returns the quality really used, or -1. */
    fun load(ctx: Context, info: StreamInfo, thumb: String?, height: Int, startMs: Long): Int {
        val p = get(ctx)
        val sel = Formats.selectVideo(info, height, allowWebm = true) ?: return -1
        val meta = MediaMetadata.Builder()
            .setTitle(info.name)
            .setArtist(info.uploaderName)
            .apply { thumb?.let { setArtworkUri(Uri.parse(it)) } }
            .build()

        val source: MediaSource
        val m = sel.muxed
        if (m != null) {
            source = ProgressiveMediaSource.Factory(factory(m.content)).createMediaSource(
                MediaItem.Builder().setUri(m.content).setMediaMetadata(meta).build()
            )
        } else {
            val v = sel.video ?: return -1
            val a = sel.audio ?: return -1
            val vs = ProgressiveMediaSource.Factory(factory(v.content)).createMediaSource(
                MediaItem.Builder().setUri(v.content).setMediaMetadata(meta).build()
            )
            val asrc = ProgressiveMediaSource.Factory(factory(a.content)).createMediaSource(
                MediaItem.fromUri(a.content)
            )
            source = MergingMediaSource(vs, asrc)
        }
        if (startMs > 0) p.setMediaSource(source, startMs) else p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        return sel.height
    }

    private fun fbFactory(): DataSource.Factory =
        DefaultHttpDataSource.Factory().setUserAgent(Net.UA_CHROME).setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)

    /** Plays a Facebook video at [height] (or the closest quality up to it). Returns the quality used, or -1. */
    fun loadFb(ctx: Context, info: Facebook.Info, height: Int, startMs: Long): Int {
        val p = get(ctx)
        val meta = MediaMetadata.Builder().setTitle(info.title).setArtist("Facebook")
            .apply { info.thumb?.let { setArtworkUri(Uri.parse(it)) } }.build()
        val reps = Facebook.bestReps(info)
        val audio = info.dashAudio
        val source: MediaSource
        val used: Int
        if (reps.isNotEmpty() && audio != null) {
            val pick = reps.filter { it.first <= height }.maxByOrNull { it.first } ?: reps.first()
            used = pick.first
            val vs = ProgressiveMediaSource.Factory(fbFactory()).createMediaSource(
                MediaItem.Builder().setUri(pick.second.url).setMediaMetadata(meta).build()
            )
            val a = ProgressiveMediaSource.Factory(fbFactory()).createMediaSource(MediaItem.fromUri(audio.url))
            source = MergingMediaSource(vs, a)
        } else {
            val url = (if (height >= 720) info.hdUrl ?: info.sdUrl else info.sdUrl ?: info.hdUrl) ?: return -1
            used = if (url == info.hdUrl) 720 else 360
            source = ProgressiveMediaSource.Factory(fbFactory()).createMediaSource(
                MediaItem.Builder().setUri(url).setMediaMetadata(meta).build()
            )
        }
        if (startMs > 0) p.setMediaSource(source, startMs) else p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        return used
    }
}
