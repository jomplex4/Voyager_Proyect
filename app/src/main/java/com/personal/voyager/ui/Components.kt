package com.personal.voyager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.personal.voyager.R
import com.personal.voyager.core.Fmt
import com.personal.voyager.core.Item
import com.personal.voyager.yt.Paged
import com.personal.voyager.yt.YtRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

@Composable
fun Ic(res: Int, modifier: Modifier = Modifier, tint: Color = Pal.White, size: Dp = 24.dp) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun IconTap(res: Int, onClick: () -> Unit, tint: Color = Pal.White, size: Dp = 24.dp, box: Dp = 48.dp) {
    Box(
        Modifier.size(box).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ic(res, tint = tint, size = size) }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Pal.Red else Pal.Card2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Txt.Button.copy(color = if (enabled) Pal.White else Pal.Gray))
    }
}

@Composable
fun Thumb(model: Any?, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp)) {
    Box(modifier.clip(shape).background(Pal.Card)) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Vinyl record: grooves, a soft highlight and the cover as the center label. Used for every audio item. */
@Composable
fun VinylThumb(model: Any?, diameter: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(diameter).drawBehind {
            val r = this.size.minDimension / 2f
            drawCircle(Color(0xFF0C0C0E))
            var rr = r * 0.97f
            while (rr > r * 0.62f) {
                drawCircle(Color(0x16FFFFFF), radius = rr, style = Stroke(width = 1f))
                rr -= r * 0.045f
            }
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, Color(0x26FFFFFF), Color.Transparent, Color(0x26FFFFFF), Color.Transparent)
                ),
                radius = r,
            )
            drawCircle(Color(0xFF2A2B2E), radius = r - 1f, style = Stroke(width = 2f))
        },
        contentAlignment = Alignment.Center,
    ) {
        Thumb(model, Modifier.size(diameter * 0.5f), CircleShape)
        Box(Modifier.size(diameter * 0.07f).clip(CircleShape).background(Color.Black))
    }
}

/** Gray rounded tile with the VOYAGER logo, the same look as the splash and the app icon. */
@Composable
fun LogoTile(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(Pal.IconBg),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.voyager_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(size * 0.74f),
        )
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 5.dp, vertical = 1.dp),
    ) { Text(text, style = Txt.Small.copy(color = Pal.White, fontSize = 11.sp)) }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTap(R.drawable.ic_arrow_back, onBack)
        Text(title, style = Txt.Title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            Column(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, style = Txt.Tab.copy(color = if (i == selected) Pal.White else Pal.Gray))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.height(2.dp).width(24.dp).background(if (i == selected) Pal.Red else Color.Transparent))
            }
        }
    }
}

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = Txt.Body.copy(color = Pal.Gray), maxLines = 4)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).border(BorderStroke(1.dp, Pal.Red), RoundedCornerShape(50))
                    .clickable(onClick = onAction).padding(horizontal = 22.dp, vertical = 10.dp),
            ) { Text(action, style = Txt.Label.copy(color = Pal.White)) }
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Pal.Red, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

// ---------------- list rows ----------------

@Composable
fun VideoRow(v: Item.Video, onClick: () -> Unit, onDownload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(150.dp).height(84.dp)) {
            Thumb(v.thumb, Modifier.fillMaxSize())
            if (v.live) Badge("LIVE", Modifier.align(Alignment.BottomEnd).padding(4.dp))
            else if (v.durationSec > 0) Badge(Fmt.duration(v.durationSec), Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = Txt.Row, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(v.uploader, style = Txt.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                Fmt.views(v.views).takeIf { v.views >= 0 && it.isNotEmpty() },
                v.dateText?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = Txt.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconTap(R.drawable.ic_download, onDownload, tint = Pal.White, size = 22.dp, box = 44.dp)
    }
}

@Composable
fun ChannelRow(c: Item.Channel, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(c.thumb, Modifier.size(64.dp), CircleShape)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = Txt.Row, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.subs >= 0) Text("${Fmt.compact(c.subs)} subscribers", style = Txt.Small)
        }
        Ic(R.drawable.ic_chevron_right, tint = Pal.Gray)
    }
}

@Composable
fun PlaylistRow(p: Item.Playlist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(150.dp).height(84.dp)) {
            Thumb(p.thumb, Modifier.fillMaxSize())
            if (p.count > 0) Badge("${p.count} videos", Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = Txt.Row, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            p.uploader?.let { Text(it, style = Txt.Small, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ---------------- pagination ----------------

/** Loads pages on demand for infinite scroll lists. */
@Stable
class Pager(
    private val scope: CoroutineScope,
    private val maxItems: Int = Int.MAX_VALUE,
    private val load: suspend (Page?) -> Paged,
) {
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf(false)
        private set

    private var next: Page? = null
    private var first = true

    /** Loads the first page once (used when a tab is shown for the first time). */
    fun ensureStarted() {
        if (first && !loading) loadMore()
    }

    fun loadMore() {
        if (loading || done) return
        loading = true
        error = null
        scope.launch {
            try {
                val r = load(if (first) null else next)
                first = false
                items = (items + r.items).take(maxItems)
                next = r.next
                if (r.next == null || r.items.isEmpty() || items.size >= maxItems) done = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = YtRepo.friendly(e)
            } finally {
                loading = false
            }
        }
    }
}
