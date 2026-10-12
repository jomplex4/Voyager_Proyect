package com.personal.voyager.dl

import com.personal.voyager.core.Net
import com.personal.voyager.fb.Facebook
import com.personal.voyager.yt.AudioKind
import com.personal.voyager.yt.Formats
import com.personal.voyager.yt.LiveStreamException
import com.personal.voyager.yt.NoStreamException
import com.personal.voyager.yt.YtRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamType

/** Turns a queued job into concrete URLs. Called again when stream URLs expire. */
object Resolver {

    suspend fun resolve(job: DlJob, allowWebm: Boolean = true): Plan = withContext(Dispatchers.IO) {
        if (job.source == "fb") resolveFb(job) else resolveYt(job, allowWebm)
    }

    private fun ytUa(url: String): String =
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) YoutubeParsingHelper.getVisionOsUserAgent(null)
        else Net.UA_DESKTOP

    private fun src(s: Stream): Src {
        val size = Formats.lengthOf(s)
        return Src(s.content, size, "${s.id}:$size", ytUa(s.content))
    }

    private suspend fun resolveYt(job: DlJob, allowWebm: Boolean): Plan {
        val info = YtRepo.stream(job.url)
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            throw LiveStreamException()
        }
        val choice = job.choice
        if (choice.startsWith("A:")) {
            val kind = AudioKind.fromTag(choice.substring(2))
            val m4a = Formats.pickM4a(info)
            if (!kind.mp3) {
                val a = m4a ?: throw NoStreamException("No audio stream found for this video")
                return Plan.M4a(src(a), kind.tag)
            }
            val a = m4a ?: Formats.pickOpus(info) ?: throw NoStreamException("No audio stream found for this video")
            return Plan.Mp3(src(a), kind.kbps, kind.tag)
        }
        val h = choice.removePrefix("V:").toIntOrNull() ?: 360
        val sel = Formats.selectVideo(info, h, allowWebm)
            ?: throw NoStreamException("No video stream found for this video")
        val tag = "${sel.height}P"
        if (sel.muxed != null) {
            val m = sel.muxed
            val webm = m.format?.suffix == "webm"
            return Plan.Direct(src(m), if (webm) "video/webm" else "video/mp4", if (webm) "webm" else "mp4", true, tag)
        }
        return Plan.Merge(src(sel.video!!), src(sel.audio!!), sel.webm, tag)
    }

    private suspend fun resolveFb(job: DlJob): Plan {
        val info = Facebook.fetch(job.url)
        val choice = job.choice
        fun mk(url: String, size: Long) = Src(url, size, "fb:$size", Net.UA_CHROME)

        if (choice.startsWith("A:")) {
            val kind = AudioKind.fromTag(choice.substring(2))
            val url = info.sdUrl ?: info.hdUrl ?: throw Facebook.FbException(Facebook.NOT_AVAILABLE)
            val s = mk(url, Facebook.probeSize(url))
            return if (kind.mp3) Plan.Mp3(s, kind.kbps, kind.tag) else Plan.M4a(s, kind.tag)
        }
        val wantHd = choice == "F:HD"
        val url = (if (wantHd) info.hdUrl ?: info.sdUrl else info.sdUrl ?: info.hdUrl)
            ?: throw Facebook.FbException(Facebook.NOT_AVAILABLE)
        val tag = if (url == info.hdUrl) "HD" else "SD"
        return Plan.Direct(mk(url, Facebook.probeSize(url)), "video/mp4", "mp4", true, tag)
    }
}
