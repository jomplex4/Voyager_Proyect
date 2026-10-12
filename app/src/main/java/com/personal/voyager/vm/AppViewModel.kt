package com.personal.voyager.vm

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.voyager.core.Fmt
import com.personal.voyager.core.Item
import com.personal.voyager.core.Links
import com.personal.voyager.core.Prefs
import com.personal.voyager.dl.Downloads
import com.personal.voyager.dl.Spec
import com.personal.voyager.fb.Facebook
import com.personal.voyager.yt.AudioKind
import com.personal.voyager.yt.Formats
import com.personal.voyager.yt.YtRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

sealed class Screen {
    data object Home : Screen()
    data class Search(val initial: String = "") : Screen()
    data class Results(val query: String) : Screen()
    data class Detail(val url: String, val title: String?, val thumb: String?) : Screen()
    data class Channel(val url: String, val name: String?) : Screen()
    data class Playlist(val url: String, val name: String?) : Screen()
    data object PlayHome : Screen()
    data object Downloading : Screen()
}

enum class Tab { DOWNLOAD, PLAY }

class ClipLink(val url: String, val thumb: String?)

class SheetRow(
    val id: String,
    val label: String,
    val subtitle: String?,
    val size: String,
    val isAudio: Boolean,
)

class SheetData(
    val title: String,
    val thumb: String?,
    val domain: String,
    val fromClipboard: Boolean,
    val audio: List<SheetRow>,
    val video: List<SheetRow>,
    val preselect: String?,
    val onConfirm: (String) -> Unit,
)

sealed class SheetUi {
    data object Loading : SheetUi()
    class Ready(val data: SheetData) : SheetUi()
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    var tab by mutableStateOf(Tab.DOWNLOAD)
    val dlStack = mutableStateListOf<Screen>(Screen.Home)
    val playStack = mutableStateListOf<Screen>(Screen.PlayHome)
    var fullscreen by mutableStateOf(false)
    var clip by mutableStateOf<ClipLink?>(null)
    var sheet by mutableStateOf<SheetUi?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private var sheetJob: Job? = null
    private var dismissedClip: String? = null

    private fun stack() = if (tab == Tab.DOWNLOAD) dlStack else playStack
    val current: Screen get() = stack().last()

    fun push(s: Screen) {
        stack().add(s)
    }

    /** Pops one screen; false when already at the root of the tab. */
    fun pop(): Boolean {
        val st = stack()
        if (st.size > 1) {
            st.removeAt(st.lastIndex)
            return true
        }
        return false
    }

    fun popToHome() {
        while (dlStack.size > 1) dlStack.removeAt(dlStack.lastIndex)
    }

    fun popToRoot() {
        val st = stack()
        while (st.size > 1) st.removeAt(st.lastIndex)
    }

    fun say(msg: String) {
        messages.tryEmit(msg)
    }

    suspend fun warmUp() {
        Downloads.libraryReady.await()
    }

    // ---------------- search / routing ----------------

    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = Links.firstUrl(t)
        if (url != null && Links.isSupported(url)) {
            openUrl(url)
            return
        }
        Prefs.addRecent(t)
        // Replace the search screen by the results so Back goes home.
        val st = dlStack
        if (st.last() is Screen.Search) st.removeAt(st.lastIndex)
        st.add(Screen.Results(t))
    }

    fun openUrl(url: String) {
        if (Links.isFacebook(url)) {
            downloadFacebook(url, false)
            return
        }
        viewModelScope.launch {
            when (YtRepo.linkType(url)) {
                StreamingService.LinkType.STREAM -> {
                    val st = dlStack
                    if (st.last() is Screen.Search) st.removeAt(st.lastIndex)
                    st.add(Screen.Detail(url, null, Links.thumbForYoutube(url)))
                }
                StreamingService.LinkType.CHANNEL -> dlStack.add(Screen.Channel(url, null))
                StreamingService.LinkType.PLAYLIST -> dlStack.add(Screen.Playlist(url, null))
                else -> say("This link is not supported")
            }
        }
    }

    // ---------------- clipboard ----------------

    fun readClipboard() {
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        val text = runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(app)?.toString()
        }.getOrNull()
        val url = Links.firstUrl(text)
        if (url == null || !Links.isSupported(url)) return
        if (url == dismissedClip || clip?.url == url) return
        clip = ClipLink(url, Links.thumbForYoutube(url))
        if (Links.isFacebook(url)) {
            viewModelScope.launch {
                val info = runCatching { Facebook.fetch(url) }.getOrNull() ?: return@launch
                if (clip?.url == url) clip = ClipLink(url, info.thumb)
            }
        }
    }

    fun dismissClip() {
        dismissedClip = clip?.url
        clip = null
    }

    fun downloadFromClip() {
        val c = clip ?: return
        if (Links.isFacebook(c.url)) {
            downloadFacebook(c.url, true)
            return
        }
        viewModelScope.launch {
            if (YtRepo.linkType(c.url) == StreamingService.LinkType.STREAM) downloadVideoUrl(c.url, true)
            else openUrl(c.url)
        }
    }

    // ---------------- format sheet ----------------

    fun dismissSheet() {
        sheetJob?.cancel()
        sheet = null
    }

    fun downloadVideoUrl(url: String, fromClipboard: Boolean = false) {
        if (Links.isFacebook(url)) {
            downloadFacebook(url, fromClipboard)
            return
        }
        sheetJob?.cancel()
        sheet = SheetUi.Loading
        sheetJob = viewModelScope.launch {
            try {
                val info = YtRepo.stream(url)
                showSheetForInfo(info, fromClipboard)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                sheet = null
                say(YtRepo.friendly(e))
            }
        }
    }

    fun showSheetForInfo(info: StreamInfo, fromClipboard: Boolean = false) {
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            sheet = null
            say("Live streams cannot be downloaded")
            return
        }
        val audio = Formats.audioOptions(info).map { o ->
            SheetRow(
                id = "A:" + o.kind.tag,
                label = o.kind.label,
                subtitle = if (o.kind.mp3) MP3_SUB else "Fast, ideal for mobile",
                size = (if (o.estimated) "≈ " else "") + Fmt.size(o.bytes),
                isAudio = true,
            )
        }
        val video = Formats.videoOptions(info).map { v ->
            SheetRow("V:${v.height}", v.label, null, (if (v.estimated) "≈ " else "") + Fmt.size(v.bytes), false)
        }
        if (audio.isEmpty() && video.isEmpty()) {
            sheet = null
            say("No downloadable streams found")
            return
        }
        val thumb = YtRepo.pickThumb(info.thumbnails) ?: Links.thumbForYoutube(info.url)
        val title = info.name ?: ""
        val url = info.url
        val dur = info.duration
        sheet = SheetUi.Ready(
            SheetData(
                title = title, thumb = thumb, domain = Links.domain(url).ifEmpty { "youtube.com" },
                fromClipboard = fromClipboard, audio = audio, video = video,
                preselect = pickPreselect(audio, video),
            ) { choice ->
                Prefs.lastChoice = choice
                enqueue(listOf(Spec("yt", url, title, thumb, dur, choice)))
                if (fromClipboard) dismissClip()
            }
        )
    }

    fun showPlaylistSheet(videos: List<Item.Video>, positions: List<Int>, title: String) {
        val n = videos.size
        val label = "≈ $n video" + if (n == 1) "" else "s"
        val audio = AudioKind.entries.map { k ->
            SheetRow("A:" + k.tag, k.label, if (k.mp3) MP3_SUB else "Fast, ideal for mobile", label, true)
        }
        val video = listOf(360, 480, 720, 1080).map { SheetRow("V:$it", "${it}p", null, label, false) }
        sheet = SheetUi.Ready(
            SheetData(
                title = title, thumb = videos.firstOrNull()?.thumb, domain = "youtube.com",
                fromClipboard = false, audio = audio, video = video,
                preselect = pickPreselect(audio, video),
            ) { choice ->
                Prefs.lastChoice = choice
                val specs = videos.mapIndexed { i, v ->
                    Spec(
                        "yt", v.url, v.title, v.thumb, v.durationSec, choice,
                        prefix = String.format("%02d - ", positions.getOrElse(i) { i + 1 }),
                    )
                }
                enqueue(specs)
            }
        )
    }

    private fun downloadFacebook(url: String, fromClipboard: Boolean) {
        sheetJob?.cancel()
        sheet = SheetUi.Loading
        sheetJob = viewModelScope.launch {
            try {
                val info = Facebook.fetch(url)
                val (hdSize, sdSize) = coroutineScope {
                    val h = info.hdUrl?.let { async { Facebook.probeSize(it) } }
                    val s = info.sdUrl?.let { async { Facebook.probeSize(it) } }
                    (h?.await() ?: -1L) to (s?.await() ?: -1L)
                }
                val audio = AudioKind.entries.map { k ->
                    val bytes = if (info.durationSec > 0) k.kbps * 125L * info.durationSec else -1L
                    SheetRow(
                        "A:" + k.tag, k.label, if (k.mp3) MP3_SUB else "Fast, ideal for mobile",
                        if (bytes > 0) "≈ " + Fmt.size(bytes) else "", true,
                    )
                }
                val video = ArrayList<SheetRow>()
                if (info.sdUrl != null) video.add(SheetRow("F:SD", "SD", null, Fmt.size(sdSize), false))
                if (info.hdUrl != null) video.add(SheetRow("F:HD", "HD", null, Fmt.size(hdSize), false))
                sheet = SheetUi.Ready(
                    SheetData(
                        title = info.title, thumb = info.thumb, domain = "facebook.com",
                        fromClipboard = fromClipboard, audio = audio, video = video,
                        preselect = pickPreselect(audio, video),
                    ) { choice ->
                        Prefs.lastChoice = choice
                        enqueue(listOf(Spec("fb", info.pageUrl, info.title, info.thumb, info.durationSec, choice)))
                        if (fromClipboard) dismissClip()
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                sheet = null
                say(YtRepo.friendly(e))
            }
        }
    }

    /** Last used choice if this sheet has it (video heights fall back to the nearest one below). */
    private fun pickPreselect(audio: List<SheetRow>, video: List<SheetRow>): String? {
        val last = Prefs.lastChoice
        if (last.isEmpty()) return null
        val all = audio + video
        if (all.any { it.id == last }) return last
        if (last.startsWith("V:")) {
            val h = last.substring(2).toIntOrNull() ?: return null
            val ids = video.mapNotNull { it.id.removePrefix("V:").toIntOrNull() }
            val best = ids.filter { it <= h }.maxOrNull() ?: ids.minOrNull() ?: return null
            return "V:$best"
        }
        if (last == "F:HD" && video.any { it.id == "F:SD" }) return "F:SD"
        return null
    }

    private fun enqueue(specs: List<Spec>) {
        val (added, dup) = Downloads.enqueue(specs)
        when {
            added > 0 && dup > 0 -> say("Added $added, $dup already downloaded")
            added > 0 -> say(if (added == 1) "Download started" else "Added $added downloads")
            dup > 0 -> say("Already downloaded")
        }
        sheet = null
    }

    companion object {
        const val MP3_SUB = "Compatible with Bluetooth speakers, phones, car stereos, smartwatches"
    }
}
