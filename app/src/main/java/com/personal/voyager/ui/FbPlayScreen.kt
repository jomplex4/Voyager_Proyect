package com.personal.voyager.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.personal.voyager.R
import com.personal.voyager.fb.Facebook
import com.personal.voyager.player.PlaybackService
import com.personal.voyager.player.PlayerHub
import com.personal.voyager.vm.AppViewModel
import com.personal.voyager.vm.Screen
import com.personal.voyager.yt.YtRepo
import kotlinx.coroutines.CancellationException

private fun fbHeights(i: Facebook.Info): List<Int> {
    val r = Facebook.bestReps(i)
    return if (r.isNotEmpty() && i.dashAudio != null) r.map { it.first }
    else listOfNotNull(if (i.sdUrl != null) 360 else null, if (i.hdUrl != null) 720 else null)
}

/** Plays a Facebook video with the same player as YouTube (quality chip, fullscreen, +/-10 s). */
@Composable
fun FbPlayScreen(vm: AppViewModel, s: Screen.FbPlay) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var info by remember(s.url) { mutableStateOf<Facebook.Info?>(null) }
    var quality by remember(s.url) { mutableStateOf(0) }
    var error by remember(s.url) { mutableStateOf<String?>(null) }
    val heights = remember(info) { info?.let { fbHeights(it) } ?: emptyList() }

    LaunchedEffect(s.url) {
        try {
            val direct = s.src
            val i = if (direct != null && s.audio != null) Facebook.Info(
                s.url, s.title ?: "Facebook video", s.thumb, null, null, 0L,
                listOf(Facebook.Rep(0, if (s.height > 0) s.height else 360, 0L, direct, "avc1")),
                Facebook.Rep(0, 0, 0L, s.audio, "mp4a"),
            )
            else if (direct != null) Facebook.Info(s.url, s.title ?: "Facebook video", s.thumb, null, direct, 0L)
            else kotlinx.coroutines.withTimeoutOrNull(40_000) { Facebook.fetch(s.url) } ?: throw java.io.IOException("Facebook took too long to answer, try again")
            info = i
            val used = PlayerHub.loadFb(ctx, i, 360, 0L)
            if (used > 0) {
                quality = used
                ctx.startService(Intent(ctx, PlaybackService::class.java))
            } else error = "No playable stream found"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = YtRepo.friendly(e)
        }
    }

    DisposableEffect(s.url) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            PlayerHub.stopPlayback()
            ctx.stopService(Intent(ctx, PlaybackService::class.java))
            vm.fullscreen = false
        }
    }

    fun pick(h: Int) {
        val i = info ?: return
        val pos = PlayerHub.get(ctx).currentPosition
        val used = PlayerHub.loadFb(ctx, i, h, pos)
        if (used > 0) quality = used
    }

    val fs = vm.fullscreen
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PreviewPlayer(vm, Modifier.fillMaxSize(), zoom = fs)
        if (!fs) Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { IconTap(R.drawable.ic_arrow_back, { vm.pop() }) }
        if (heights.isNotEmpty() && quality > 0) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { QualityChip(heights, quality) { pick(it) } }
        }
        if (info == null && error == null) {
            CircularProgressIndicator(color = Pal.Red, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        error?.let {
            Text(it, style = Txt.Body.copy(color = Pal.Gray), modifier = Modifier.align(Alignment.Center).padding(32.dp))
        }
    }
}
