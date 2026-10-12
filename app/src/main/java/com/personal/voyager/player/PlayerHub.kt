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

    /** Loads a light preview (360p preferred, never above 480p). Returns false when nothing playable exists. */
    fun load(ctx: Context, info: StreamInfo, thumb: String?): Boolean {
        val p = get(ctx)
        val meta = MediaMetadata.Builder()
            .setTitle(info.name)
            .setArtist(info.uploaderName)
            .apply { thumb?.let { setArtworkUri(Uri.parse(it)) } }
            .build()

        val muxed = info.videoStreams
            .filter { progressive(it) && Formats.labelHeight(it) in 100..480 }
            .minByOrNull { abs(Formats.labelHeight(it) - 360) }

        val source: MediaSource
        if (muxed != null) {
            val f = factory(muxed.content)
            source = ProgressiveMediaSource.Factory(f).createMediaSource(
                MediaItem.Builder().setUri(muxed.content).setMediaMetadata(meta).build()
            )
        } else {
            val v = info.videoOnlyStreams
                .filter { progressive(it) && Formats.labelHeight(it) in 100..480 }
                .sortedWith(compareBy({ if (it.format == MediaFormat.MPEG_4) 0 else 1 }, { abs(Formats.labelHeight(it) - 360) }))
                .firstOrNull() ?: return false
            val a = Formats.pickM4a(info) ?: Formats.pickOpus(info) ?: return false
            val f = factory(v.content)
            val vs = ProgressiveMediaSource.Factory(f).createMediaSource(
                MediaItem.Builder().setUri(v.content).setMediaMetadata(meta).build()
            )
            val asrc = ProgressiveMediaSource.Factory(factory(a.content)).createMediaSource(
                MediaItem.fromUri(a.content)
            )
            source = MergingMediaSource(vs, asrc)
        }
        p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        return true
    }
}
