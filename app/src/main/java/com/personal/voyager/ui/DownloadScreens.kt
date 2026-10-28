package com.personal.voyager.ui

import android.content.Intent
import android.view.View
import android.view.LayoutInflater
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.personal.voyager.R
import com.personal.voyager.core.Item
import com.personal.voyager.core.Links
import com.personal.voyager.core.Prefs
import com.personal.voyager.player.PlaybackService
import com.personal.voyager.player.PlayerHub
import com.personal.voyager.vm.AppViewModel
import com.personal.voyager.vm.ClipLink
import com.personal.voyager.vm.Screen
import com.personal.voyager.yt.ChannelHeader
import com.personal.voyager.yt.PlaylistHeader
import com.personal.voyager.yt.YtRepo
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

// ---------------- home: copied links ----------------

@Composable
fun ClipRow(vm: AppViewModel, c: ClipLink) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Pal.Card2).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(c.thumb, Modifier.size(width = 64.dp, height = 48.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (Links.isFacebook(c.url)) "Facebook" else "YouTube", style = Txt.Row)
            Text(Links.short(c.url, 26), style = Txt.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Pal.Red).clickable { vm.downloadFromClip(c) }
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) { Text("Download", style = Txt.Label.copy(color = Pal.White)) }
        IconTap(R.drawable.ic_close, { vm.dismissClip(c) }, tint = Pal.Gray, size = 18.dp, box = 36.dp)
    }
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Same proportions as the reference screenshot: logo about 43% of the width, magnifier at the right.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.dm_logo),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.padding(start = w * (35f / 720f)).width(w * (307f / 720f)),
                )
                Spacer(Modifier.weight(1f))
                IconTap(R.drawable.ic_person, { vm.push(Screen.Facebook) }, size = 24.dp)
                IconTap(R.drawable.ic_search, { vm.push(Screen.Search()) }, size = 26.dp)
                Spacer(Modifier.width(8.dp))
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                vm.purgeClips()
                delay(20_000)
            }
        }
        if (vm.clips.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Copy a YouTube or Facebook link and it will show up here",
                    style = Txt.Body.copy(color = Pal.Gray),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(vm.clips) { _, c -> ClipRow(vm, c) }
            }
        }
    }
}

// ---------------- search ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: AppViewModel, initial: String) {
    var text by remember { mutableStateOf(initial) }
    var recents by remember { mutableStateOf(Prefs.recents()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(Unit) { YtRepo.warmUp() }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(text) {
        val q = text.trim()
        if (q.length < 2 || Links.firstUrl(q) != null) {
            suggestions = emptyList()
        } else {
            delay(220)
            suggestions = YtRepo.suggestions(q)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(52.dp)
                .clip(RoundedCornerShape(16.dp)).background(Pal.Card2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(R.drawable.ic_arrow_back, { vm.pop() })
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = Txt.Body,
                cursorBrush = SolidColor(Pal.Red),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submit(text) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text("Search or paste a link", style = Txt.Body.copy(color = Pal.Gray))
                        inner()
                    }
                },
            )
            if (text.isNotEmpty()) IconTap(R.drawable.ic_close, { text = "" }, tint = Pal.Gray, size = 20.dp)
        }
        if (text.isNotBlank() && suggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                suggestions.forEach { sg ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.submit(sg) }.padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ic(R.drawable.ic_search, tint = Pal.Gray, size = 20.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(sg, style = Txt.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else if (text.isBlank() && recents.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent searches", style = Txt.Label, modifier = Modifier.weight(1f))
                IconTap(R.drawable.ic_delete, { Prefs.clearRecents(); recents = emptyList() }, tint = Pal.Gray, size = 20.dp)
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recents.forEach { r ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Pal.Card2).clickable { vm.submit(r) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text(r, style = Txt.Body.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

// ---------------- generic item list ----------------

@Composable
fun ItemRow(vm: AppViewModel, item: Item) {
    when (item) {
        is Item.Video -> VideoRow(
            item,
            onClick = { vm.push(Screen.Detail(item.url, item.title, item.thumb)) },
            onDownload = { vm.downloadVideoUrl(item.url) },
        )
        is Item.Channel -> ChannelRow(item) { vm.push(Screen.Channel(item.url, item.title)) }
        is Item.Playlist -> PlaylistRow(item) { vm.push(Screen.Playlist(item.url, item.title)) }
    }
}

@Composable
fun ItemList(
    vm: AppViewModel,
    pager: Pager,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(pager) { pager.ensureStarted() }
    LazyColumn(modifier.fillMaxSize(), state = state) {
        header?.invoke(this)
        itemsIndexed(pager.items) { _, item -> ItemRow(vm, item) }
        item {
            when {
                pager.error != null -> CenterMessage(pager.error ?: "", action = "Retry", onAction = { pager.loadMore() })
                !pager.done -> {
                    LaunchedEffect(pager.items.size) { pager.loadMore() }
                    Loading()
                }
                pager.items.isEmpty() -> CenterMessage("No results")
            }
        }
    }
}

// ---------------- results ----------------

@Composable
fun ResultsScreen(vm: AppViewModel, query: String) {
    // Everything below is kept in the ViewModel: coming back from a video shows the same list at the same place.
    var tab by vm.keepOf("res:$query:tab") { mutableIntStateOf(0) }
    val filters = listOf(YtRepo.ALL, YtRepo.CHANNELS, YtRepo.PLAYLISTS)
    val pagers = vm.keepOf("res:$query:pagers") { filters.map { f -> Pager(vm.uiScope) { page -> YtRepo.search(query, f, page) } } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(52.dp)
                .clip(RoundedCornerShape(16.dp)).background(Pal.Card2).clickable { vm.push(Screen.Search(query)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(R.drawable.ic_arrow_back, { vm.pop() })
            Text(query, style = Txt.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconTap(R.drawable.ic_close, { vm.push(Screen.Search()) }, tint = Pal.Gray, size = 20.dp)
        }
        Tabs(listOf("All", "Channels", "Playlists"), tab, { tab = it })
        Spacer(Modifier.height(4.dp))
        ItemList(vm, pagers[tab], state = vm.keepOf("res:$query:list$tab") { LazyListState() })
    }
}

// ---------------- video detail ----------------

@Composable
fun PreviewPlayer(vm: AppViewModel, modifier: Modifier, zoom: Boolean = false) {
    AndroidView(
        factory = { c ->
            val v = LayoutInflater.from(c).inflate(R.layout.player_view, null, false) as PlayerView
            v.player = PlayerHub.get(c)
            v.setFullscreenButtonClickListener { fs -> vm.fullscreen = fs }
            // The gear of the player bar opens the quality and speed options.
            v.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.setOnClickListener { vm.settingsOpen = true }
            v
        },
        update = {
            it.setFullscreenButtonState(vm.fullscreen)
            // Fullscreen fills the whole screen (the video is zoomed to cover it instead of showing black bars).
            it.resizeMode = if (zoom) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

private fun saveResume(i: StreamInfo, posMs: Long) {
    if (i.duration <= 600) return
    val id = i.id ?: return
    val st = PlayerHub.peek()?.playbackState
    // Finished (ended, or in the last seconds): the saved point is deleted. A partial watch keeps it.
    if (st == Player.STATE_ENDED || posMs >= i.duration * 1000 - 10_000) Prefs.resumeClear(id)
    else if (posMs > 5_000) Prefs.resumeSet(id, posMs)
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Pal.Red else Pal.Card2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, style = Txt.Label.copy(color = Pal.White)) }
}

/** Options of the player gear: video quality and playback speed. */
@Composable
fun PlayerSettings(vm: AppViewModel, heights: List<Int>, current: Int, onQuality: (Int) -> Unit) {
    if (!vm.settingsOpen) return
    val ctx = LocalContext.current
    var speed by remember { mutableStateOf(PlayerHub.get(ctx).playbackParameters.speed) }
    androidx.compose.ui.window.Dialog(onDismissRequest = { vm.settingsOpen = false }) {
        if (vm.fullscreen) {
            val w = (LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            androidx.compose.runtime.SideEffect {
                w?.let {
                    androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
                        .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF1B1B1D)).verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (heights.isNotEmpty() && current > 0) {
                Text("Quality", style = Txt.Label.copy(color = Pal.Gray))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    heights.forEach { h -> OptionChip("${h}p", h == current) { vm.settingsOpen = false; onQuality(h) } }
                }
                Spacer(Modifier.height(20.dp))
            }
            Text("Speed", style = Txt.Label.copy(color = Pal.Gray))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                    OptionChip(if (sp == 1f) "Normal" else "${sp}x", sp == speed) {
                        speed = sp
                        PlayerHub.get(ctx).setPlaybackSpeed(sp)
                        vm.settingsOpen = false
                    }
                }
            }
            if (vm.fullscreen) {
                Spacer(Modifier.height(20.dp))
                Text("Screen", style = Txt.Label.copy(color = Pal.Gray))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OptionChip("Original size", !vm.fillScreen) { vm.fillScreen = false; vm.settingsOpen = false }
                    OptionChip("Fill the screen", vm.fillScreen) { vm.fillScreen = true; vm.settingsOpen = false }
                }
            }
        }
    }
}

@Composable
fun FillToggle(vm: AppViewModel) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000)).clickable { vm.fillScreen = !vm.fillScreen }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(if (vm.fillScreen) "Original" else "Fill", style = Txt.Label.copy(color = Pal.White)) }
}

@Composable
fun QualityChip(heights: List<Int>, current: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0x99000000)).clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("${current}p", style = Txt.Label.copy(color = Pal.White)) }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            heights.forEach { h ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("${h}p", style = Txt.Body.copy(color = if (h == current) Pal.RedBright else Pal.White)) },
                    onClick = { open = false; onPick(h) },
                )
            }
        }
    }
}

@Composable
fun DetailScreen(vm: AppViewModel, s: Screen.Detail) {
    val ctx = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    // The player in the notification bar needs notifications to be on. If they are off, ask (up to 3 times) and
    // afterwards take the person to the notification settings of the app (twice at most).
    LaunchedEffect(Unit) {
        if (!androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            if (android.os.Build.VERSION.SDK_INT >= 33 && Prefs.notifPrompts < 3 &&
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Prefs.notifPrompts = Prefs.notifPrompts + 1
                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else if (Prefs.notifSettingsShown < 2) {
                Prefs.notifSettingsShown = Prefs.notifSettingsShown + 1
                vm.say("Turn on notifications for VOYAGER to see the player controls in the notification bar")
                runCatching {
                    ctx.startActivity(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
    var info by remember(s.url) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(s.url) { mutableStateOf<String?>(null) }
    var previewFailed by remember(s.url) { mutableStateOf(false) }
    var quality by remember(s.url) { mutableStateOf(0) }
    var thumbUrl by remember(s.url) { mutableStateOf<String?>(null) }
    val heights = remember(info) { info?.let { com.personal.voyager.yt.Formats.availableHeights(it) } ?: emptyList() }
    val related = remember(info) { info?.let { YtRepo.related(it) } ?: emptyList() }

    LaunchedEffect(s.url) {
        try {
            val i = YtRepo.stream(s.url)
            info = i
            if (i.streamType == StreamType.LIVE_STREAM || i.streamType == StreamType.AUDIO_LIVE_STREAM) {
                previewFailed = true
                return@LaunchedEffect
            }
            val thumb = YtRepo.pickThumb(i.thumbnails) ?: s.thumb
            thumbUrl = thumb
            // Videos over 10 minutes continue from where they were left.
            val saved = if (i.duration > 600) Prefs.resumeGet(i.id ?: "") else 0L
            val used = PlayerHub.load(ctx, i, thumb, PlayerHub.defaultHeight(i), saved)
            if (used > 0) {
                quality = used
                if (saved > 0) vm.say("Continuing from " + com.personal.voyager.core.Fmt.duration(saved / 1000))
                ctx.startService(Intent(ctx, PlaybackService::class.java))
            } else previewFailed = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = YtRepo.friendly(e)
        }
    }

    // Leaving fullscreen always returns to the video's own proportions.
    LaunchedEffect(vm.fullscreen) { if (!vm.fullscreen) vm.fillScreen = false }

    // Save the position every few seconds (also while the app is in the background).
    LaunchedEffect(info) {
        val i = info ?: return@LaunchedEffect
        if (i.duration <= 600) return@LaunchedEffect
        while (true) {
            delay(5000)
            PlayerHub.peek()?.let { saveResume(i, it.currentPosition) }
        }
    }

    fun pick(h: Int) {
        val i = info ?: return
        val pos = PlayerHub.get(ctx).currentPosition
        val used = PlayerHub.load(ctx, i, thumbUrl, h, pos)
        if (used > 0) quality = used else vm.say("That quality is not available")
    }

    PlayerSettings(vm, heights, quality) { pick(it) }

    DisposableEffect(s.url) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                previewFailed = true
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                view.keepScreenOn = playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) info?.id?.let { Prefs.resumeClear(it) }
            }
        }
        view.keepScreenOn = true
        val player = PlayerHub.get(ctx)
        player.addListener(listener)
        onDispose {
            info?.let { saveResume(it, player.currentPosition) }
            player.removeListener(listener)
            view.keepScreenOn = false
            PlayerHub.stopPlayback()
            ctx.stopService(Intent(ctx, PlaybackService::class.java))
            vm.fullscreen = false
        }
    }

    // The player is never reconfigured when the app goes to the background or comes back: touching the video
    // track made ExoPlayer drop its buffer and start loading again.

    if (vm.fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PreviewPlayer(vm, Modifier.fillMaxSize(), zoom = vm.fillScreen)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            PreviewPlayer(vm, Modifier.fillMaxSize())
            if (previewFailed || error != null) {
                Text(
                    if (error != null) "" else "Preview unavailable, you can still download",
                    style = Txt.Small.copy(color = Pal.White),
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
            Box(Modifier.align(Alignment.TopStart).padding(4.dp)) {
                IconTap(R.drawable.ic_arrow_back, { vm.pop() })
            }

        }
        val i = info
        LazyColumn(Modifier.fillMaxSize()) {
            if (i == null && error == null) item { Loading() }
            if (error != null) item {
                CenterMessage(error ?: "", action = "Back", onAction = { vm.pop() })
            }
            if (i != null) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(i.name ?: "", style = Txt.Title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        val chUrl = i.uploaderUrl
                        Text(
                            i.uploaderName ?: "",
                            style = Txt.Label.copy(color = if (chUrl.isNullOrBlank()) Pal.Gray else Pal.White),
                            modifier = Modifier.clickable(enabled = !chUrl.isNullOrBlank()) {
                                vm.push(Screen.Channel(chUrl ?: "", i.uploaderName))
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                        PillButton("Download", { vm.showSheetForInfo(i) }, Modifier.fillMaxWidth())
                    }
                }
                if (related.isNotEmpty()) {
                    item {
                        Text("Related videos", style = Txt.Title.copy(fontSize = 16.sp), modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                    }
                    itemsIndexed(related) { _, it -> ItemRow(vm, it) }
                }
            }
        }
    }
}

// ---------------- channel ----------------

@Composable
fun ChannelScreen(vm: AppViewModel, s: Screen.Channel) {
    var header by vm.keepOf("ch:${s.url}:header") { mutableStateOf<ChannelHeader?>(null) }
    var error by remember(s.url) { mutableStateOf<String?>(null) }
    var tab by vm.keepOf("ch:${s.url}:tab") { mutableIntStateOf(0) }

    LaunchedEffect(s.url) {
        if (header != null) return@LaunchedEffect
        try {
            header = YtRepo.channel(s.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = YtRepo.friendly(e)
        }
    }
    val h = header
    Column(Modifier.fillMaxSize()) {
        TopBar(h?.name ?: s.name ?: "Channel", { vm.pop() })
        when {
            error != null -> CenterMessage(error ?: "")
            h == null -> Loading()
            else -> {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(h.avatar, Modifier.size(64.dp), CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(h.name, style = Txt.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (h.subs >= 0) Text("${com.personal.voyager.core.Fmt.compact(h.subs)} subscribers", style = Txt.Small)
                    }
                }
                Tabs(listOf("Videos", "Playlists"), tab, { tab = it })
                Spacer(Modifier.height(4.dp))
                val pagers = vm.keepOf("ch:${s.url}:pagers") {
                    listOf(h.videosTab, h.playlistsTab).map { handler ->
                        handler?.let { hd -> Pager(vm.uiScope) { page -> YtRepo.channelTab(hd, page) } }
                    }
                }
                val p = pagers[tab]
                if (p == null) CenterMessage("Nothing here")
                else ItemList(vm, p, state = vm.keepOf("ch:${s.url}:list$tab") { LazyListState() })
            }
        }
    }
}

// ---------------- playlist (courses) ----------------

@Composable
fun PlaylistScreen(vm: AppViewModel, s: Screen.Playlist) {
    var header by vm.keepOf("pl:${s.url}:header") { mutableStateOf<PlaylistHeader?>(null) }
    var error by remember(s.url) { mutableStateOf<String?>(null) }
    val selected: SnapshotStateList<String> = vm.keepOf("pl:${s.url}:sel") { mutableStateListOf<String>() }

    LaunchedEffect(s.url) {
        if (header != null) return@LaunchedEffect
        try {
            header = YtRepo.playlist(s.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = YtRepo.friendly(e)
        }
    }
    val h = header
    Column(Modifier.fillMaxSize()) {
        TopBar(h?.name ?: s.name ?: "Playlist", { vm.pop() })
        when {
            error != null -> CenterMessage(error ?: "")
            h == null -> Loading()
            else -> {
                val pager = vm.keepOf("pl:${s.url}:pager") {
                    Pager(vm.uiScope, maxItems = 200) { page -> if (page == null) h.first else YtRepo.playlistMore(s.url, page) }
                }
                LaunchedEffect(pager) { pager.ensureStarted() }
                val videos = pager.items.filterIsInstance<Item.Video>()
                val allSelected = videos.isNotEmpty() && videos.all { it.url in selected }

                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(h.uploader, if (h.count > 0) "${h.count} videos" else null).joinToString(" · "),
                        style = Txt.Small, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).clickable {
                            if (allSelected) selected.clear() else {
                                selected.clear()
                                selected.addAll(videos.map { it.url })
                            }
                        }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ic(if (allSelected) R.drawable.ic_check_circle else R.drawable.ic_circle_outline, tint = if (allSelected) Pal.Red else Pal.Gray, size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Select all", style = Txt.Label.copy(color = Pal.White))
                    }
                }
                Box(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), state = vm.keepOf("pl:${s.url}:list") { LazyListState() }) {
                        itemsIndexed(videos) { _, v ->
                            val on = v.url in selected
                            Row(
                                Modifier.fillMaxWidth().clickable { if (on) selected.remove(v.url) else selected.add(v.url) }
                                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Ic(if (on) R.drawable.ic_check_circle else R.drawable.ic_circle_outline, tint = if (on) Pal.Red else Pal.Gray, size = 24.dp)
                                Spacer(Modifier.width(12.dp))
                                Box(Modifier.width(120.dp).height(68.dp)) {
                                    Thumb(v.thumb, Modifier.fillMaxSize(), RoundedCornerShape(8.dp))
                                    if (v.durationSec > 0) Badge(com.personal.voyager.core.Fmt.duration(v.durationSec), Modifier.align(Alignment.BottomEnd).padding(3.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(v.title, style = Txt.Row, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            when {
                                pager.error != null -> CenterMessage(pager.error ?: "", action = "Retry", onAction = { pager.loadMore() })
                                !pager.done -> {
                                    LaunchedEffect(pager.items.size) { pager.loadMore() }
                                    Loading()
                                }
                                videos.isEmpty() -> CenterMessage("This playlist is empty")
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    PillButton(
                        if (selected.isEmpty()) "Download" else "Download (${selected.size})",
                        onClick = {
                            val order = videos.withIndex().filter { it.value.url in selected }
                            vm.showPlaylistSheet(order.map { it.value }, order.map { it.index + 1 }, h.name)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selected.isNotEmpty(),
                    )
                }
            }
        }
    }
}
