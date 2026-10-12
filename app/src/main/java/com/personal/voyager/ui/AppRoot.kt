package com.personal.voyager.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.personal.voyager.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.voyager.dl.Downloads
import com.personal.voyager.dl.Storage
import com.personal.voyager.vm.AppViewModel
import com.personal.voyager.vm.Screen
import com.personal.voyager.vm.SheetUi
import com.personal.voyager.vm.Tab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge

/** The splash only lives as long as the app needs to load (this is a small floor so it does not flicker). */
private const val SPLASH_MIN_MS = 350L

@Composable
fun VoyagerRoot(vm: AppViewModel) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val t0 = SystemClock.uptimeMillis()
        vm.warmUp()
        val left = SPLASH_MIN_MS - (SystemClock.uptimeMillis() - t0)
        if (left > 0) delay(left)
        ready = true
    }
    val granted = rememberStorageGranted()
    LaunchedEffect(granted) { if (granted) Storage.prepare() }
    if (!ready) {
        SplashScreen()
    } else if (!granted) {
        StorageGate()
    } else {
        AppRoot(vm)
    }
}

@Composable
private fun rememberStorageGranted(): Boolean {
    var granted by remember { mutableStateOf(Storage.granted()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = Storage.granted() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return granted
}

/** One-time screen: Android only lets an app create a folder in the main storage with "All files access". */
@Composable
private fun StorageGate() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Pal.Black).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        LogoTile(88.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
        Text("One quick step", style = Txt.Title)
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        Text(
            "VOYAGER saves everything in its own folder called Voyager, so your music and video player can find it. " +
                "Android asks you to allow \"All files access\" once.",
            style = Txt.Body.copy(color = Pal.Gray),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
        PillButton("Allow access", {
            val pkg = Uri.parse("package:" + ctx.packageName)
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
            } catch (e: Exception) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val fs = vm.fullscreen
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(Unit) {
        merge(vm.messages, Downloads.messages).collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(fs) {
        val a = activity ?: return@LaunchedEffect
        a.requestedOrientation =
            if (fs) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val c = WindowCompat.getInsetsController(a.window, a.window.decorView)
        if (fs) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = true) {
        when {
            vm.sheet != null -> vm.dismissSheet()
            vm.fullscreen -> vm.fullscreen = false
            vm.pop() -> Unit
            vm.tab == Tab.PLAY -> vm.tab = Tab.DOWNLOAD
            else -> activity?.finish()
        }
    }

    Column(Modifier.fillMaxSize().background(Pal.Black)) {
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .then(if (fs) Modifier else Modifier.statusBarsPadding()),
        ) {
            when (val s = vm.current) {
                is Screen.Home -> HomeScreen(vm)
                is Screen.Search -> SearchScreen(vm, s.initial)
                is Screen.Results -> ResultsScreen(vm, s.query)
                is Screen.Detail -> DetailScreen(vm, s)
                is Screen.Channel -> ChannelScreen(vm, s)
                is Screen.Playlist -> PlaylistScreen(vm, s)
                is Screen.PlayHome -> PlayHomeScreen(vm)
                is Screen.Downloading -> DownloadingScreen(vm)
                is Screen.Facebook -> FacebookScreen(vm)
            }
        }
        if (!fs && !imeVisible) BottomBar(vm)
    }

    if (vm.fbLoginPrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.fbLoginPrompt = false },
            containerColor = Pal.Card2,
            title = { Text("Log in to Facebook", style = Txt.Title) },
            text = {
                Text(
                    "This video is private or needs an account. Log in once and VOYAGER can download the videos you are allowed to watch.",
                    style = Txt.Body.copy(color = Pal.Gray),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.fbLoginPrompt = false
                    vm.tab = Tab.DOWNLOAD
                    vm.push(Screen.Facebook)
                }) { Text("Log in", style = Txt.Label.copy(color = Pal.RedBright)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { vm.fbLoginPrompt = false }) {
                    Text("Not now", style = Txt.Label.copy(color = Pal.White))
                }
            },
        )
    }

    when (val sh = vm.sheet) {
        is SheetUi.Ready -> FormatSheet(sh.data) { vm.dismissSheet() }
        is SheetUi.Loading -> LoadingOverlay { vm.dismissSheet() }
        null -> Unit
    }
}

@Composable
private fun BottomBar(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth().background(Pal.Black).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Line))
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            BarItem(Modifier.weight(1f), "Download", R.drawable.ic_search, vm.tab == Tab.DOWNLOAD) {
                if (vm.tab == Tab.DOWNLOAD) {
                    vm.popToHome()
                } else {
                    vm.tab = Tab.DOWNLOAD
                }
            }
            BarItem(Modifier.weight(1f), "Play", R.drawable.ic_library, vm.tab == Tab.PLAY) {
                if (vm.tab == Tab.PLAY) {
                    vm.popToRoot()
                } else {
                    vm.tab = Tab.PLAY
                }
            }
        }
    }
}

@Composable
private fun BarItem(modifier: Modifier, label: String, icon: Int, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Ic(icon, tint = if (active) Pal.Red else Pal.Gray, size = 24.dp)
        Text(label, style = Txt.Label.copy(color = if (active) Pal.White else Pal.Gray, fontSize = 12.sp))
    }
}
