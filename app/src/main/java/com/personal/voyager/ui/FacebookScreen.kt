package com.personal.voyager.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

/**
 * The normal Facebook mobile site inside the app, with one extra thing: a download button on every video, reel and
 * photo (feed, pages, channels, search). VOYAGER never sees the password; the login stays in the WebView cookies.
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun FacebookScreen(vm: AppViewModel) {
    var loggedIn by remember { mutableStateOf(FbSession.loggedIn()) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("Loading…") }
    var failed by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else vm.pop()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Facebook", { vm.pop() }) {
            if (loggedIn) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Pal.Card2)
                        .clickable {
                            FbSession.logout()
                            loggedIn = false
                            web?.loadUrl(LOGIN_LITE)
                            vm.say("Logged out of Facebook")
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Log out", style = Txt.Label.copy(color = Pal.White)) }
                Spacer(Modifier.width(8.dp))
            }
        }
        if (status.isNotEmpty()) {
            Text(status, style = Txt.Small.copy(color = Pal.Gray), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (failed) {
            PillButton("Update WebView in Play Store", {
                val ui = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.webview"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(ui)
                } catch (e: Exception) {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }, Modifier.padding(16.dp).fillMaxWidth())
        }
        AndroidView(
            factory = { c ->
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                WebView(c).apply {
                    web = this
                    setBackgroundColor(Color.BLACK)
                    cm.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    // A plain mobile Chrome identity: the embedded-browser markers made Facebook serve a blank page.
                    val chrome = Regex("Chrome/[\\d.]+").find(settings.userAgentString)?.value ?: "Chrome/110.0.0.0"
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 11; SM-A032F) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Mobile Safari/537.36"
                    addJavascriptInterface(Bridge(vm), "Voyager")
                    webChromeClient = WebChromeClient()
                    val mobileUa = settings.userAgentString
                    val wvVersion = runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "unknown"
                    // Different pages and identities, tried in order, because the phone's WebView may not be able to
                    // draw one of them (the page loads to about 80% and stays white).
                    val attempts = listOf(
                        LOGIN_LITE to mobileUa,
                        "https://free.facebook.com/login.php" to mobileUa,
                        "https://touch.facebook.com/login.php" to mobileUa,
                        "https://www.facebook.com/login/" to Net.UA_CHROME,
                    )
                    var attempt = 0
                    var usedLiteHome = false
                    status = "Loading… (WebView $wvVersion)"

                    var blankRef: (Int) -> Unit = {}
                    fun blankCheckLater(my: Int) {
                        postDelayed({ blankRef(my) }, 10000)
                    }

                    fun nextAttempt(reason: String) {
                        attempt++
                        if (attempt < attempts.size) {
                            settings.userAgentString = attempts[attempt].second
                            status = "$reason Trying another page (${attempt + 1}/${attempts.size})…"
                            loadUrl(attempts[attempt].first)
                            blankCheckLater(attempt)
                        } else {
                            failed = true
                            status = "Facebook cannot be drawn by this phone's WebView (version $wvVersion). Update it and try again."
                        }
                    }
                    fun blankCheck(my: Int) {
                        evaluateJavascript("(document.body ? document.body.innerText.length : 0)") { len ->
                            if (my == attempt && (len?.toIntOrNull() ?: 0) < 40) {
                                if (FbSession.loggedIn()) {
                                    if (!usedLiteHome) {
                                        usedLiteHome = true
                                        status = "Using the simple Facebook page"
                                        loadUrl(HOME_LITE)
                                    }
                                } else nextAttempt("Blank page.")
                            }
                        }
                    }
                    blankRef = { blankCheck(it) }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (!failed && (status.startsWith("Loading") || status.isEmpty())) {
                                status = if (newProgress < 100) "Loading $newProgress% (WebView $wvVersion)" else ""
                            }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            checkLogin(view)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            checkLogin(view)
                            view?.evaluateJavascript(INJECT, null)
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) nextAttempt("Could not load (" + (error?.description ?: "error") + ").")
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
                                    failed = false
                                    settings.userAgentString = mobileUa
                                    view?.loadUrl(HOME)
                                    attempt = attempts.size // later blank checks only fall back to the simple home page
                                    view?.postDelayed({ blankCheck(attempt) }, 10000)
                                }
                            }
                        }
                    }
                    loadUrl(if (FbSession.loggedIn()) HOME else LOGIN_LITE)
                    postDelayed({ blankCheck(attempt) }, 10000)
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
