package com.personal.voyager.yt

import com.personal.voyager.dl.Storage
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

/** Writes what the extractor returned for a video (formats, qualities, delivery) into Voyager/youtube-report.txt. */
object YtReport {
    /** The real reason a video could not be read (YouTube's own message), for youtube-report.txt. */
    fun error(url: String, e: Throwable) {
        runCatching {
            Storage.prepare()
            val f = File(Storage.root(), "youtube-report.txt")
            if (f.exists() && f.length() > 120_000) f.delete()
            f.appendText(
                "=== " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) +
                    "  ERROR " + url + "\n  " + e.javaClass.simpleName + ": " + (e.message ?: "").replace("\n", " ").take(300) + "\n"
            )
        }
    }

    fun write(info: StreamInfo) {
        runCatching {
            val sb = StringBuilder()
            sb.append("=== ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
            sb.append("  ").append(info.name).append("  type=").append(info.streamType).append(" dur=").append(info.duration).append("s")
            sb.append(" age=").append(info.ageLimit).append('\n')
            sb.append("videoOnly: ").append(
                info.videoOnlyStreams.groupBy { "${it.format?.name}/${(it.codec ?: "").take(5)}/${it.height}p/${it.deliveryMethod}" }
                    .map { "${it.key} x${it.value.size}" }.joinToString("; ").ifEmpty { "none" }
            ).append('\n')
            sb.append("muxed: ").append(
                info.videoStreams.groupBy { "${it.format?.name}/${it.height}p/${it.deliveryMethod}" }
                    .map { "${it.key} x${it.value.size}" }.joinToString("; ").ifEmpty { "none" }
            ).append('\n')
            sb.append("audio: ").append(
                info.audioStreams.groupBy { "${it.format?.name}/${it.averageBitrate}k/${it.deliveryMethod}/${it.audioTrackType}" }
                    .map { "${it.key} x${it.value.size}" }.joinToString("; ").ifEmpty { "none" }
            ).append('\n')
            sb.append("dashManifest=").append(!info.dashMpdUrl.isNullOrEmpty()).append(" hls=").append(!info.hlsUrl.isNullOrEmpty()).append('\n')
            Storage.prepare()
            val f = File(Storage.root(), "youtube-report.txt")
            if (f.exists() && f.length() > 120_000) f.delete()
            f.appendText(sb.toString())
        }
    }
}
