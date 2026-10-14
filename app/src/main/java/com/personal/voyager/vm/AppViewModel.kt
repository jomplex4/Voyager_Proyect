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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.personal.voyager.fb.Photo
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
    data object Facebook : Screen()
}

enum class Tab { DOWNLOAD, PLAY }

class ClipLink(val url: String, val thumb: String?, val at: Long, val key: String)

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
    domain: String,
    val fromClipboard: Boolean,
    audioInit: List<SheetRow>,
    videoInit: List<SheetRow>,
    val preselect: String?,
    val onConfirm: (String) -> Unit,
) {
    var domain by mutableStateOf(domain)
    var audio by mutableStateOf(audioInit)
    var video by mutableStateOf(videoInit)
}

sealed class SheetUi {
    data object Loading : SheetUi()
    class Ready(val data: SheetData) : SheetUi()
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    var tab by mutableStateOf(Tab.DOWNLOAD)
    val dlStack = mutableStateListOf<Screen>(Screen.Home)
    val playStack = mutableStateListOf<Screen>(Screen.PlayHome)
    var fullscreen by mutableStateOf(false)
    var fbLoginPrompt by mutableStateOf(false)
    val clips = mutableStateListOf<ClipLink>()
    var sheet by mutableStateOf<SheetUi?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private var sheetJob: Job? = null
    private val dismissed = LinkedHashSet<String>()
    private var pendingClip: String? = null

    init {
        Prefs.clips().forEach { clips.add(ClipLink(it.url, it.thumb, it.at, it.key)) }
        purgeClips()
        dismissed.addAll(Prefs.dismissedClips())
    }

    private fun saveClips() {
        Prefs.saveClips(clips.map { Prefs.ClipRec(it.url, it.thumb, it.at, it.key) })
        Prefs.saveDismissed(dismissed.toList().takeLast(40))
    }

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

    /** Copied links live for 10 minutes: nothing piles up and nothing stale is shown. */
    fun purgeClips() {
        val now = System.currentTimeMillis()
        val old = clips.filter { now - it.at > CLIP_TTL_MS }
        if (old.isNotEmpty()) {
            clips.removeAll(old.toSet())
            old.forEach { dismissed.add(it.key) }
            saveClips()
        }
    }

    fun readClipboard() {
        purgeClips()
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        val text = runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(app)?.toString()
        }.getOrNull()
        val url = Links.firstUrl(text)
        if (url == null || !Links.isSupported(url)) return
        val ts = runCatching { cm.primaryClipDescription?.timestamp ?: 0L }.getOrDefault(0L)
        val now = System.currentTimeMillis()
        if (ts > 0 && now - ts > CLIP_TTL_MS) return
        val key = "$url@$ts"
        if (key in dismissed || clips.any { it.key == key }) return
        clips.removeAll { it.url == url }
        clips.add(0, ClipLink(url, Links.thumbForYoutube(url), if (ts > 0) ts else now, key))
        while (clips.size > 30) clips.removeAt(clips.lastIndex)
        saveClips()
        if (Links.isFacebook(url)) {
            viewModelScope.launch {
                val info = runCatching { Facebook.fetch(url) }.getOrNull() ?: return@launch
                val idx = clips.indexOfFirst { it.key == key }
                if (idx >= 0) {
                    val c = clips[idx]
                    clips[idx] = ClipLink(url, info.thumb, c.at, c.key)
                    saveClips()
                }
            }
        }
    }

    fun dismissClip(c: ClipLink) {
        clips.remove(c)
        dismissed.add(c.key)
        saveClips()
    }

    /** Called after a download starts from a copied link: that link leaves the list. */
    fun dismissClip() {
        val k = pendingClip ?: return
        pendingClip = null
        clips.firstOrNull { it.key == k }?.let { dismissClip(it) }
    }

    fun downloadFromClip(c: ClipLink) {
        pendingClip = c.key
        if (Links.isFacebook(c.url)) {
            downloadFacebook(c.url, true)
            return
        }
        viewModelScope.launch {
            if (YtRepo.linkType(c.url) == StreamingService.LinkType.STREAM) downloadVideoUrl(c.url, true)
            else openUrl(c.url)
        }
    }

    fun savePhoto(src: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Photo.save(app, src) }
            say(if (ok) "Photo saved in the Voyager folder" else "Could not save the photo")
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
        val uploader = info.uploaderName ?: ""
        sheet = SheetUi.Ready(
            SheetData(
                title = title, thumb = thumb, domain = Links.domain(url).ifEmpty { "youtube.com" },
                fromClipboard = fromClipboard, audioInit = audio, videoInit = video,
                preselect = pickPreselect(audio, video),
            ) { choice ->
                Prefs.lastChoice = choice
                enqueue(listOf(Spec("yt", url, title, thumb, dur, choice, uploader = uploader)))
                if (fromClipboard) dismissClip()
            }
        )
    }

    fun showPlaylistSheet(videos: List<Item.Video>, positions: List<Int>, title: String) {
        val n = videos.size
        val totalSec = videos.sumOf { it.durationSec }
        val domain = "youtube.com · $n video" + if (n == 1) "" else "s"
        fun est(bytes: Long) = if (bytes > 0) "≈ " + Fmt.size(bytes) else ""
        val typicalMbps = listOf(360 to 0.6, 480 to 0.9, 720 to 1.6, 1080 to 3.0)

        val audio = AudioKind.entries.map { k ->
            SheetRow("A:" + k.tag, k.label, if (k.mp3) MP3_SUB else "Fast, ideal for mobile", est(k.kbps * 125L * totalSec), true)
        }
        val video = typicalMbps.map { (h, m) -> SheetRow("V:$h", "${h}p", null, est((m * 125_000 * totalSec).toLong()), false) }
        val data = SheetData(
            title = title, thumb = videos.firstOrNull()?.thumb, domain = domain,
            fromClipboard = false, audioInit = audio, videoInit = video,
            preselect = pickPreselect(audio, video),
        ) { choice ->
            Prefs.lastChoice = choice
            val specs = videos.mapIndexed { i, v ->
                Spec(
                    "yt", v.url, v.title, v.thumb, v.durationSec, choice,
                    prefix = String.format("%02d - ", positions.getOrElse(i) { i + 1 }),
                    uploader = v.uploader,
                )
            }
            enqueue(specs)
        }
        sheet = SheetUi.Ready(data)

        // Small selections get the real totals (the extraction is cached, so the downloads start faster too).
        if (n <= 6) {
            viewModelScope.launch {
                val infos = coroutineScope {
                    videos.map { v -> async { runCatching { YtRepo.stream(v.url) }.getOrNull() } }.map { it.await() }
                }
                if (infos.any { it == null }) return@launch
                val ok = infos.filterNotNull()
                data.audio = AudioKind.entries.map { k ->
                    val total = ok.sumOf { i -> Formats.audioOptions(i).firstOrNull { it.kind == k }?.bytes ?: 0L }
                    SheetRow("A:" + k.tag, k.label, if (k.mp3) MP3_SUB else "Fast, ideal for mobile", est(total), true)
                }
                data.video = typicalMbps.map { (h, _) ->
                    val total = ok.sumOf { i -> Formats.selectVideo(i, h)?.bytes ?: 0L }
                    SheetRow("V:$h", "${h}p", null, est(total), false)
                }
            }
        }
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
                        fromClipboard = fromClipboard, audioInit = audio, videoInit = video,
                        preselect = pickPreselect(audio, video),
                    ) { choice ->
                        Prefs.lastChoice = choice
                        enqueue(listOf(Spec("fb", info.pageUrl, info.title, info.thumb, info.durationSec, choice, uploader = "Facebook")))
                        if (fromClipboard) dismissClip()
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                sheet = null
                if (e is Facebook.FbException && e.loginNeeded) fbLoginPrompt = true
                else say(YtRepo.friendly(e))
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
        const val CLIP_TTL_MS = 10 * 60 * 1000L
        const val MP3_SUB = "Compatible with Bluetooth speakers, phones, car stereos, smartwatches"
    }
}
