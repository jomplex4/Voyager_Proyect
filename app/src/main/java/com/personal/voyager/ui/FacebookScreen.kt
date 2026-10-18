package com.personal.voyager.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.voyager.core.Net
import com.personal.voyager.fb.FbSession
import com.personal.voyager.vm.AppViewModel

private const val HOME = "https://m.facebook.com/"
private const val LOGIN = "https://m.facebook.com/login/"
private const val LOGIN_LITE = "https://mbasic.facebook.com/login/"
private const val HOME_LITE = "https://mbasic.facebook.com/"

/** Adds a red download button to every video, reel and photo of the normal Facebook mobile site. */
private val INJECT = """
(function(){
  if (window.__voyager) return; window.__voyager = 1;
  var st = document.createElement('style');
  st.textContent = '.vy-btn{position:absolute;top:8px;right:8px;z-index:2147483000;width:42px;height:42px;border-radius:21px;background:#DC0000;display:flex;align-items:center;justify-content:center;box-shadow:0 1px 5px rgba(0,0,0,.6)}.vy-btn svg{width:24px;height:24px;fill:#fff}';
  document.head.appendChild(st);
  var ICON = '<svg viewBox="0 0 24 24"><path d="M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z"/></svg>';
  var SEL = 'a[href*="/reel/"],a[href*="/videos/"],a[href*="/watch"],a[href*="story_fbid"],a[href*="/posts/"],a[href*="permalink"]';
  function permalink(el){
    var n = el;
    for (var i = 0; i < 12 && n; i++, n = n.parentElement) {
      var a = n.querySelector && n.querySelector(SEL);
      if (a && a.href) return a.href;
    }
    return location.href;
  }
  function add(host, kind, payload){
    if (!host || host.getAttribute('data-vy')) return;
    host.setAttribute('data-vy', '1');
    if (getComputedStyle(host).position === 'static') host.style.position = 'relative';
    var b = document.createElement('div');
    b.className = 'vy-btn'; b.innerHTML = ICON;
    var go = function(e){
      e.stopPropagation(); e.preventDefault();
      if (kind === 'video') Voyager.video(payload()); else Voyager.photo(payload());
    };
    b.addEventListener('click', go, true);
    b.addEventListener('touchend', go, true);
    host.appendChild(b);
  }
  function scan(){
    var vs = document.querySelectorAll('video');
    for (var i = 0; i < vs.length; i++) { (function(v){ add(v.parentElement, 'video', function(){ return permalink(v); }); })(vs[i]); }
    var is = document.querySelectorAll('img');
    for (var j = 0; j < is.length; j++) {
      var m = is[j];
      if (m.clientWidth >= 220 && m.clientHeight >= 160 && /fbcdn|scontent/.test(m.src)) {
        (function(m){ add(m.parentElement, 'photo', function(){ return m.src; }); })(m);
      }
    }
  }
  var t = null;
  new MutationObserver(function(){ if (t) return; t = setTimeout(function(){ t = null; scan(); }, 700); })
    .observe(document.body, {childList: true, subtree: true});
  scan();
})();
"""

private class Bridge(private val vm: AppViewModel) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun video(url: String) {
        main.post { vm.downloadVideoUrl(url) }
    }

    @JavascriptInterface
    fun photo(src: String) {
        main.post { vm.savePhoto(src) }
    }
}

/** 1 when a login field is really visible on screen (not just present in the page), 0 otherwise. */
private const val VISIBLE_INPUT =
    "(function(){var i=document.querySelectorAll('input[type=password],input[type=email],input[type=text],input[type=tel]');" +
        "for(var k=0;k<i.length;k++){var r=i[k].getBoundingClientRect();" +
        "if(r.width>20&&r.height>10&&getComputedStyle(i[k]).visibility!='hidden')return 1}return 0})()"

/** Ways to show Facebook, tried in order: the first that draws a visible login field wins. */
private class Mode(val login: String, val home: String, val desktop: Boolean, val software: Boolean)

private val MODES = listOf(
    Mode("https://m.facebook.com/login/", "https://m.facebook.com/", desktop = false, software = false),
    Mode("https://m.facebook.com/login/", "https://m.facebook.com/", desktop = false, software = true),
    Mode("https://www.facebook.com/login/", "https://www.facebook.com/", desktop = true, software = true),
    Mode("https://mbasic.facebook.com/login/", "https://mbasic.facebook.com/", desktop = false, software = true),
)

/**
 * The normal Facebook site inside the app, with a download button on every video, reel and photo once logged in.
 * VOYAGER never sees the password; the login stays in the WebView cookies.
 *
 * The page is shown with the WebView's own default identity (a custom one made Facebook answer with an empty page).
 * If no login field is visible after a few seconds the next mode is tried (software drawing, desktop page, simple
 * page). The "Mode" button switches by hand.
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun FacebookScreen(vm: AppViewModel) {
    var loggedIn by remember { mutableStateOf(FbSession.loggedIn()) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Loading…") }
    var consoleError by remember { mutableStateOf("") }
    var defaultUa by remember { mutableStateOf("") }
    val wv = remember { runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "?" }

    fun useMode(w: WebView, i: Int) {
        val m = MODES[i]
        w.settings.userAgentString = if (m.desktop) Net.UA_CHROME else defaultUa
        w.setLayerType(if (m.software) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE, null)
        consoleError = ""
        status = "Mode ${i + 1}/${MODES.size} · WebView $wv · Loading…"
        w.loadUrl(if (FbSession.loggedIn()) m.home else m.login)
    }

    fun scheduleCheck(w: WebView, my: Int) {
        w.postDelayed({
            if (my != mode || loggedIn) return@postDelayed
            w.evaluateJavascript(VISIBLE_INPUT) { r ->
                if (my == mode && !loggedIn && r != "1") {
                    if (my + 1 < MODES.size) {
                        mode = my + 1
                        useMode(w, mode)
                        scheduleCheck(w, mode)
                    } else {
                        status = "Facebook could not be drawn in any mode. Tap Mode to try again."
                    }
                }
            }
        }, 9000)
    }

    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else vm.pop()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Facebook", { vm.pop() }) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Pal.Card2)
                    .clickable {
                        val w = web
                        if (w != null) {
                            mode = (mode + 1) % MODES.size
                            useMode(w, mode)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("Mode ${mode + 1}/${MODES.size}", style = Txt.Label.copy(color = Pal.White)) }
            Spacer(Modifier.width(6.dp))
            if (loggedIn) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Pal.Card2)
                        .clickable {
                            FbSession.logout()
                            loggedIn = false
                            web?.let { useMode(it, mode) }
                            vm.say("Logged out of Facebook")
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Log out", style = Txt.Label.copy(color = Pal.White)) }
                Spacer(Modifier.width(8.dp))
            }
        }
        if (status.isNotEmpty()) {
            Text(status, style = Txt.Small.copy(color = Pal.Gray), modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }
        if (consoleError.isNotEmpty()) {
            Text(consoleError, style = Txt.Small.copy(color = Pal.RedBright), maxLines = 2, modifier = Modifier.padding(horizontal = 16.dp))
        }
        AndroidView(
            factory = { c ->
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                WebView(c).apply {
                    web = this
                    cm.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    defaultUa = settings.userAgentString
                    addJavascriptInterface(Bridge(vm), "Voyager")
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            val base = "Mode ${mode + 1}/${MODES.size} · WebView $wv"
                            status = if (newProgress < 100) "$base · Loading $newProgress%" else base
                        }

                        override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                            if (m?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) consoleError = (m.message() ?: "").take(160)
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            checkLogin(view)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            checkLogin(view)
                            if (FbSession.loggedIn()) view?.evaluateJavascript(INJECT, null)
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) status = "Could not load: " + (error?.description ?: "error")
                        }

                        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                            if (request?.isForMainFrame == true) status = "Facebook answered with error " + (errorResponse?.statusCode ?: 0)
                        }

                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.cancel()
                            status = "Secure connection failed. Check the phone's date and time."
                        }

                        private fun checkLogin(view: WebView?) {
                            if (FbSession.loggedIn()) {
                                cm.flush()
                                if (!loggedIn) {
                                    loggedIn = true
                                    view?.loadUrl(MODES[mode].home)
                                }
                            }
                        }
                    }
                    useMode(this, 0)
                    if (!loggedIn) scheduleCheck(this, 0)
                }
            },
            onRelease = {
                web = null
                it.destroy()
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
