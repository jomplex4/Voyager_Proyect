package com.personal.voyager.yt

import com.personal.voyager.core.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import kotlin.math.abs

class LiveStreamException : Exception("Live streams cannot be downloaded")
class NoStreamException(msg: String) : Exception(msg)

class Paged(val items: List<Item>, val next: Page?)

class ChannelHeader(
    val name: String,
    val avatar: String?,
    val subs: Long,
    val videosTab: ListLinkHandler?,
    val playlistsTab: ListLinkHandler?,
)

class PlaylistHeader(
    val name: String,
    val uploader: String?,
    val count: Long,
    val thumb: String?,
    val first: Paged,
)

object YtRepo {
    private val service get() = ServiceList.YouTube

    const val ALL: String = "all"
    val VIDEOS: String = YoutubeSearchQueryHandlerFactory.VIDEOS
    val CHANNELS: String = YoutubeSearchQueryHandlerFactory.CHANNELS
    val PLAYLISTS: String = YoutubeSearchQueryHandlerFactory.PLAYLISTS

    fun pickThumb(list: List<Image>?): String? {
        if (list.isNullOrEmpty()) return null
        return list.minByOrNull { abs((if (it.height > 0) it.height else 360) - 360) }?.url
    }

    private fun toItem(i: InfoItem): Item? = when (i) {
        is StreamInfoItem -> Item.Video(
            url = i.url,
            title = i.name ?: "",
            thumb = pickThumb(i.thumbnails),
            uploader = i.uploaderName ?: "",
            uploaderUrl = i.uploaderUrl,
            durationSec = i.duration,
            views = i.viewCount,
            dateText = i.textualUploadDate,
            live = i.streamType == StreamType.LIVE_STREAM || i.streamType == StreamType.AUDIO_LIVE_STREAM,
        )
        is ChannelInfoItem -> Item.Channel(
            url = i.url,
            title = i.name ?: "",
            thumb = pickThumb(i.thumbnails),
            subs = i.subscriberCount,
        )
        is PlaylistInfoItem -> Item.Playlist(
            url = i.url,
            title = i.name ?: "",
            thumb = pickThumb(i.thumbnails),
            uploader = i.uploaderName,
            count = i.streamCount,
        )
        else -> null
    }

    suspend fun search(query: String, filter: String, page: Page?): Paged = withContext(Dispatchers.IO) {
        val filters = if (filter == ALL) emptyList() else listOf(filter)
        val handler = service.searchQHFactory.fromQuery(query, filters, "")
        if (page == null) {
            val info = SearchInfo.getInfo(service, handler)
            Paged(info.relatedItems.mapNotNull { toItem(it) }, if (info.hasNextPage()) info.nextPage else null)
        } else {
            val p = SearchInfo.getMoreItems(service, handler, page)
            Paged(p.items.mapNotNull { toItem(it) }, if (p.hasNextPage()) p.nextPage else null)
        }
    }

    suspend fun channel(url: String): ChannelHeader = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(service, url)
        val tabs = info.tabs
        ChannelHeader(
            name = info.name ?: "",
            avatar = pickThumb(info.avatars),
            subs = info.subscriberCount,
            videosTab = tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) },
            playlistsTab = tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.PLAYLISTS) },
        )
    }

    suspend fun channelTab(handler: ListLinkHandler, page: Page?): Paged = withContext(Dispatchers.IO) {
        if (page == null) {
            val info = ChannelTabInfo.getInfo(service, handler)
            Paged(info.relatedItems.mapNotNull { toItem(it) }, if (info.hasNextPage()) info.nextPage else null)
        } else {
            val p = ChannelTabInfo.getMoreItems(service, handler, page)
            Paged(p.items.mapNotNull { toItem(it) }, if (p.hasNextPage()) p.nextPage else null)
        }
    }

    suspend fun playlist(url: String): PlaylistHeader = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(service, url)
        PlaylistHeader(
            name = info.name ?: "",
            uploader = info.uploaderName,
            count = info.streamCount,
            thumb = pickThumb(info.thumbnails),
            first = Paged(
                info.relatedItems.mapNotNull { toItem(it) },
                if (info.hasNextPage()) info.nextPage else null,
            ),
        )
    }

    suspend fun playlistMore(url: String, page: Page): Paged = withContext(Dispatchers.IO) {
        val p = PlaylistInfo.getMoreItems(service, url, page)
        Paged(p.items.mapNotNull { toItem(it) }, if (p.hasNextPage()) p.nextPage else null)
    }

    private class Cached(val info: StreamInfo, val at: Long)

    /** Last few extractions, so Detail -> Download -> job start do not extract the same video three times. */
    private val cache = object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > 10
    }

    suspend fun stream(url: String, fresh: Boolean = false): StreamInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!fresh) {
            val hit = synchronized(cache) { cache[url]?.takeIf { now - it.at < 10 * 60_000 } }
            if (hit != null) return@withContext hit.info
        }
        val info = try {
            StreamInfo.getInfo(service, url)
        } catch (e: Throwable) {
            YtReport.error(url, e)
            throw e
        }
        synchronized(cache) {
            cache[url] = Cached(info, now)
            cache[info.url] = Cached(info, now)
        }
        info
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { service.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList()).take(8)
    }

    @Volatile
    private var warmed = false

    /** Primes the connection and YouTube's session data while the user is still typing. */
    suspend fun warmUp() {
        if (warmed) return
        warmed = true
        runCatching { search("a", VIDEOS, null) }
    }

    fun related(info: StreamInfo): List<Item> = info.relatedItems.mapNotNull { toItem(it) }

    suspend fun linkType(url: String): StreamingService.LinkType? = withContext(Dispatchers.IO) {
        runCatching { service.getLinkTypeByUrl(url) }.getOrNull()
    }

    fun friendly(e: Throwable): String = when (e) {
        is LiveStreamException -> "Live streams cannot be downloaded"
        is NoStreamException -> e.message ?: "No downloadable stream found"
        is ReCaptchaException -> "YouTube asked for verification, try again later"
        is AgeRestrictedContentException -> "This video is age restricted"
        is PaidContentException -> "This is paid content and cannot be downloaded"
        is GeographicRestrictionException -> "This video is not available in your country"
        is SignInConfirmNotBotException -> "YouTube asked to confirm you are not a bot, try again later"
        is PrivateContentException -> "This video is private"
        is ContentNotAvailableException -> "YouTube does not give this video to the app: " + (e.message ?: "private, removed or restricted").replace("\n", " ").take(150)
        is ContentNotSupportedException -> "This content is not supported"
        is IOException -> {
            val m = e.message ?: ""
            if (m.startsWith("HTTP") || m.contains("could not be read") || m.contains("no video or audio") || m.contains("took too long") || m.contains("cannot play") || m.contains("without sound")) m
            else "No connection"
        }
        is ExtractionException -> "Could not read this video. YouTube may have changed (see CONTEXTO.md: update the extractor)"
        else -> e.message ?: "Something went wrong"
    }
}
