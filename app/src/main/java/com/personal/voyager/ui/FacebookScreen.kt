package com.personal.voyager.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.personal.voyager.R
import com.personal.voyager.core.Net
import com.personal.voyager.fb.FbSession
import com.personal.voyager.fb.MediaSniffer
import com.personal.voyager.vm.AppViewModel
import com.personal.voyager.vm.Screen
import java.io.File

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
    Mode("https://mbasic.facebook.com/login/", "https://m.facebook.com/", desktop = false, software = true),
    Mode("https://www.facebook.com/login/", "https://m.facebook.com/", desktop = true, software = true),
)
private const val HOME = "https://m.facebook.com/"

private fun isLoginPage(url: String?): Boolean {
    val u = android.net.Uri.parse(url ?: return false)
    return (u.path ?: "").startsWith("/login") || (u.path ?: "").startsWith("/checkpoint")
}

/**
 * Buttons for Facebook (videos, reels, grid tiles) + light mode. On a video or reel page there is ONE download button (fixed on the right side, away from the "open app" banner) for the video on screen. In the feed
 * every video gets both; reel tiles of a channel get a small download button; plain photos get a photo button.
 * Light mode: videos do not start by themselves in the feed and page animations are off. Taps are ignored for 2.5 s.
 */
private val INJECT = """
(function(){
  if (window.__voyager) return; window.__voyager = 1;
  var VIDEO_PAGE = /\/(reel|reels|watch|videos)(\/|\?|$)|\/share\/[vr]\//;
  var LINK = 'a[href*="/reel/"],a[href*="/videos/"],a[href*="/watch"],a[href*="story_fbid"],a[href*="/posts/"],a[href*="permalink"],a[href*="/share/v/"],a[href*="/share/r/"]';
  var VLINK = 'a[href*="/reel/"],a[href*="/videos/"],a[href*="/watch"],a[href*="/share/v/"],a[href*="/share/r/"]';
  var DL = '<svg viewBox="0 0 24 24"><path d="M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z"/></svg>';
  var PLAY = '<svg viewBox="0 0 24 24"><path d="M8,5v14l11,-7z"/></svg>';
  var st = document.createElement('style');
  st.textContent = '*{animation:none!important;transition:none!important}.vy-btn{position:absolute;top:8px;left:8px;z-index:2147483647;width:42px;height:42px;border-radius:21px;background:#DC0000;display:flex;align-items:center;justify-content:center;box-shadow:0 1px 5px rgba(0,0,0,.6)}.vy-btn svg{width:24px;height:24px;fill:#fff}.vy-play{top:56px!important;background:#2a2a2a!important}.vy-sm{width:30px;height:30px;border-radius:15px;top:4px;left:4px}.vy-sm svg{width:18px;height:18px}.vy-fixed{position:fixed!important;left:auto!important;top:38%!important;right:10px!important}.vy-fixed.vy-play{top:calc(38% + 52px)!important}';
  document.head.appendChild(st);
  var last = 0;
  window.__vyTouch = 0;
  var urlSet = {}, urlN = 0, progSet = {};
  function harvest(text){
    if (!text || text.length > 4000000 || text.indexOf('.mp4') < 0) return;
    var t = text.split('\\/').join('/').split('\\u0025').join('%').split('\\u0026').join('&').split('&amp;').join('&');
    var re = /https:\/\/[^"'\s\\<>]+?\.mp4[^"'\s\\<>]*/g, m;
    while ((m = re.exec(t)) && urlN < 1500) { var u = m[0]; if (/fbcdn|facebook/.test(u) && !urlSet[u]) { urlSet[u] = 1; urlN++; } }
    // The complete MP4 (picture and sound together) that Facebook itself offers for the video.
    var kre = /"(browser_native_hd_url|browser_native_sd_url|playable_url_quality_hd|playable_url|hd_src|sd_src)"\s*:\s*"(https:[^"]+)"/g, k;
    while ((k = kre.exec(t)) && urlN < 1500) { var pu = k[2]; if (!progSet[pu]) { progSet[pu] = k[1]; urlSet[pu] = 1; urlN++; } }
  }
  var ofetch = window.fetch;
  if (ofetch) window.fetch = function(){
    var p = ofetch.apply(this, arguments);
    try { p.then(function(r){ try { var ct = r.headers.get('content-type') || ''; if (/graphql|video|reel|watch/.test(r.url || '') && /json|text|javascript/.test(ct)) r.clone().text().then(harvest); } catch (e) {} }); } catch (e) {}
    return p;
  };
  var oxs = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function(){
    this.addEventListener('load', function(){ try { if (this.responseType === '' || this.responseType === 'text') harvest(this.responseText); } catch (e) {} });
    return oxs.apply(this, arguments);
  };
  function all(){
    var sc = document.scripts, tot = 0;
    for (var i = 0; i < sc.length; i++) { var x = sc[i].textContent; if (x && x.indexOf('.mp4') >= 0) { tot += x.length; if (tot > 6000000) break; harvest(x); } }
    return Object.keys(urlSet).slice(-800).map(function(u){ return progSet[u] ? 'P|' + progSet[u] + '|' + u : u; }).join('\n');
  }
  document.addEventListener('touchstart', function(){ window.__vyTouch = Date.now(); }, true);
  var op = HTMLMediaElement.prototype.play;
  HTMLMediaElement.prototype.play = function(){
    if (!VIDEO_PAGE.test(location.pathname + location.search) && Date.now() - window.__vyTouch > 1500) {
      try { this.pause(); } catch (e) {}
      return Promise.resolve();
    }
    return op.apply(this, arguments);
  };
  function permalink(el){
    var n = el;
    for (var i = 0; i < 12 && n; i++, n = n.parentElement) {
      var a = n.querySelector && n.querySelector(LINK);
      if (a && a.href) return a.href;
    }
    return location.href;
  }
  function bestVideo(){
    var vs = document.querySelectorAll('video'), best = null, area = 0;
    for (var i = 0; i < vs.length; i++) {
      var r = vs[i].getBoundingClientRect();
      var w = Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0));
      var h = Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
      if (w * h > area) { area = w * h; best = vs[i]; }
    }
    return best;
  }
  function srcOf(v){ var x = v && (v.currentSrc || v.src); return (x && /^https?:/.test(x)) ? x : ''; }
  function poster(v){ return (v && v.poster) ? v.poster : ''; }
  var reqs = [];
  try {
    new PerformanceObserver(function(l){
      var es = l.getEntries();
      for (var i = 0; i < es.length; i++) { var n = es[i].name; if (/\.mp4/.test(n) && /fbcdn|facebook/.test(n)) { reqs.push(n); if (reqs.length > 400) reqs.shift(); } }
    }).observe({type: 'resource', buffered: true});
  } catch (e) {}
  function res(){ return reqs.slice(-150).join('\n'); }
  function withVideo(v, cb){ if (v && v.paused) { try { v.play(); } catch (e) {} setTimeout(cb, 1800); } else cb(); }
  function fire(fn){ var now = Date.now(); if (now - last < 2500) return; last = now; fn(); }
  function mk(cls, icon, onTap){
    var b = document.createElement('div'); b.className = 'vy-btn ' + cls; b.innerHTML = icon;
    var go = function(e){ e.stopPropagation(); e.preventDefault(); fire(onTap); };
    b.addEventListener('click', go, true); b.addEventListener('touchend', go, true);
    return b;
  }
  function prep(host){
    if (getComputedStyle(host).position === 'static') host.style.position = 'relative';
  }
  function add(host, getUrl, v){
    if (!host || host.getAttribute('data-vy')) return;
    host.setAttribute('data-vy', '1'); prep(host);
    host.appendChild(mk('', DL, function(){ withVideo(v, function(){ Voyager.video(getUrl(), srcOf(v), document.title, poster(v), v ? res() : '-', v ? all() : ''); }); }));
  }
  function addTile(a){
    if (a.getAttribute('data-vy')) return;
    a.setAttribute('data-vy', '1'); prep(a);
    a.appendChild(mk('vy-sm', DL, function(){ Voyager.video(a.href, '', document.title, '', '-', ''); }));
  }
  function scan(){
    var fixed = document.querySelectorAll('.vy-fixed');
    if (VIDEO_PAGE.test(location.pathname + location.search)) {
      if (!fixed.length) {
        var u = function(){ var v = bestVideo(); return v ? permalink(v) : location.href; };
        document.body.appendChild(mk('vy-fixed', DL, function(){ var v = bestVideo(); withVideo(v, function(){ Voyager.video(u(), srcOf(v), document.title, poster(v), res(), all()); }); }));
      }
      return;
    }
    for (var f = 0; f < fixed.length; f++) fixed[f].remove();
    var as = document.querySelectorAll(VLINK);
    for (var t = 0; t < as.length; t++) {
      var a = as[t];
      if (a.getAttribute('data-vy')) continue;
      var r = a.getBoundingClientRect();
      if (r.width >= 70 && r.width <= 260 && r.height >= 90 && r.height <= 520 && (a.querySelector('img') || a.querySelector('[style*="background-image"]'))) addTile(a);
    }
    var vs = document.querySelectorAll('video');
    for (var i = 0; i < vs.length; i++) (function(v){ add(v.parentElement, function(){ return permalink(v); }, v); })(vs[i]);
    var is = document.querySelectorAll('img');
    for (var j = 0; j < is.length; j++) {
      var m = is[j];
      if (m.clientWidth >= 220 && m.clientHeight >= 160 && /fbcdn|scontent/.test(m.src)) {
        (function(m){
          if (m.closest('[data-vy]')) return;
          var n = m, link = null;
          for (var k = 0; k < 5 && n; k++, n = n.parentElement) {
            var q = n.querySelector && n.querySelector(VLINK);
            if (q && q.href) { link = q.href; break; }
          }
          if (link) add(m.parentElement, function(){ return link; }, null);
          else if (!m.parentElement.getAttribute('data-vy')) {
            m.parentElement.setAttribute('data-vy', '1'); prep(m.parentElement);
            m.parentElement.appendChild(mk('', DL, function(){ Voyager.photo(m.src); }));
          }
        })(m);
      }
    }
  }
  var t0 = null;
  new MutationObserver(function(){ if (t0) return; t0 = setTimeout(function(){ t0 = null; scan(); }, 700); })
    .observe(document.body, {childList: true, subtree: true});
  scan();
})();
"""

/**
 * Tells Facebook's player that this phone cannot play AV1, VP9 or HEVC, so it sends H.264 (the format every player
 * opens). It runs before the page's own scripts when the WebView supports it, and again when the page starts.
 */
private const val NO_NEW_CODECS = """
(function(){
  if (window.__vyCodec) return; window.__vyCodec = 1;
  var bad = /av01|av1|vp09|vp9|hev1|hvc1|hevc/i;
  try { var its = MediaSource.isTypeSupported; MediaSource.isTypeSupported = function(t){ return bad.test(t || '') ? false : its.call(MediaSource, t); }; } catch (e) {}
  try { var cpt = HTMLMediaElement.prototype.canPlayType; HTMLMediaElement.prototype.canPlayType = function(t){ return bad.test(t || '') ? '' : cpt.call(this, t); }; } catch (e) {}
  try {
    var mc = navigator.mediaCapabilities;
    if (mc && mc.decodingInfo) { var di = mc.decodingInfo.bind(mc); mc.decodingInfo = function(c){ if (bad.test(JSON.stringify(c || {}))) return Promise.resolve({supported: false, smooth: false, powerEfficient: false}); return di(c); }; }
  } catch (e) {}
})();
"""

private class Bridge(private val vm: AppViewModel) {
    private val main = Handler(Looper.getMainLooper())
    private val videoLink = Regex("/reel|/videos/|/watch|story_fbid|/posts/|permalink|/share/[vr]/|[?&]v=")

    private fun clean(t: String) = t.replace(Regex("\\s*\\|\\s*Facebook\\s*$"), "").trim().ifEmpty { "Facebook video" }

    /**
     * [src] is the file the page plays directly (empty when it uses a stream); [res] are the media addresses the
     * player asked for ("-" for a grid tile: nothing is playing, so the page is read instead).
     */
    @JavascriptInterface
    fun video(url: String, src: String, title: String, poster: String, res: String, all: String) {
        main.post {
            if (vm.sheet != null) return@post
            if (res != "-") {
                MediaSniffer.noteAll(res)
                MediaSniffer.noteAvailable(all)
            }
            val id = Regex("(?:/reel/|/videos/|[?&]v=)(\\d{6,})").find(url)?.groupValues?.get(1)
            val cands = if (res != "-" && src.isEmpty()) MediaSniffer.candidates(id) else emptyList()
            when {
                src.isNotEmpty() -> vm.downloadFbSrc(src, url, clean(title), poster.ifEmpty { null })
                cands.isNotEmpty() -> vm.downloadFbCandidates(cands, clean(title), poster.ifEmpty { null }, MediaSniffer.pick(id))
                !videoLink.containsMatchIn(url) -> vm.say("Open the video first, then tap download")
                else -> vm.downloadVideoUrl(url)
            }
        }
    }

    @JavascriptInterface
    fun photo(src: String) {
        main.post { vm.savePhoto(src) }
    }
}

/** True when the WebView is drawing nothing but white (checked on the real picture, not on the page text). */
private fun looksBlank(w: WebView): Boolean {
    val bw = w.width / 4
    val bh = w.height / 4
    if (bw <= 0 || bh <= 0) return false
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
    return try {
        val c = Canvas(bmp)
        c.scale(0.25f, 0.25f)
        w.draw(c)
        var dark = 0
        var y = 0
        while (y < bh) {
            var x = 0
            while (x < bw) {
                val p = bmp.getPixel(x, y)
                if ((p and 0xFF) < 0xF0 || ((p shr 8) and 0xFF) < 0xF0 || ((p shr 16) and 0xFF) < 0xF0) dark++
                x += 4
            }
            y += 4
        }
        dark == 0
    } catch (e: Throwable) {
        false
    } finally {
        bmp.recycle()
    }
}

private fun dirSize(f: File): Long = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

/**
 * Full screen Facebook (the normal site) with download buttons. Only a round back arrow floats on top. The login
 * stays in the WebView cookies; VOYAGER never sees the password. The screen stays on while this page is in front.
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun FacebookScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Never trust a saved cookie: the page itself decides. It always starts on the simple page, which draws on
    // this phone; if it shows the login, the user types the data there; if it shows the account, the full site opens.
    var loggedIn by remember { mutableStateOf(FbSession.loggedIn()) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    var defaultUa by remember { mutableStateOf("") }
    var dbg by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    var freshHome by remember { mutableStateOf(false) }

    fun useMode(w: WebView, i: Int) {
        val m = MODES[i]
        touched = false
        if (loggedIn) {
            w.settings.userAgentString = defaultUa
            w.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            freshHome = true
            w.loadUrl(HOME)
        } else {
            w.settings.userAgentString = if (m.desktop) Net.UA_CHROME else defaultUa
            w.setLayerType(if (m.software) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE, null)
            w.loadUrl(m.login)
        }
    }

    fun goHome(w: WebView) {
        w.settings.userAgentString = defaultUa
        w.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        freshHome = true
        w.loadUrl(HOME)
    }

    // After loading: check the real picture. Logged out and nothing visible: next mode. Logged in and the page is
    // blank (saved session no longer works): forget it and restart the login modes, like on a fresh install.
    fun scheduleCheck(w: WebView, my: Int) {
        w.postDelayed({
            if (my != mode) return@postDelayed
            val blank = looksBlank(w)
            dbg = "mode ${my + 1}/${MODES.size} · logged=$loggedIn · blank=$blank"
            if (loggedIn) {
                // hardware drawing cannot be read back: a dead session is handled by the login redirect or a long press
            } else {
                w.evaluateJavascript(VISIBLE_INPUT) { r ->
                    if (my == mode && !loggedIn && my + 1 < MODES.size &&
                        ((!touched && (r != "1" || blank)) || (blank && MODES[my].software))
                    ) {
                        mode = my + 1
                        useMode(w, mode)
                        scheduleCheck(w, mode)
                    }
                }
            }
        }, if (loggedIn) 12000 else 8000)
    }

    // Long press on the back arrow: forget the saved login and start the login modes from the beginning.
    fun resetLogin() {
        FbSession.logout()
        loggedIn = false
        val w = web ?: return
        mode = 0
        useMode(w, 0)
        scheduleCheck(w, 0)
        vm.say("Login reset")
    }

    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else vm.pop()
    }

    Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
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
                    setOnTouchListener { _, _ ->
                        touched = true
                        false
                    }
                    var homeTries = 0
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        runCatching { WebViewCompat.addDocumentStartJavaScript(this, NO_NEW_CODECS, setOf("https://*.facebook.com", "https://facebook.com")) }
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            view?.evaluateJavascript(NO_NEW_CODECS, null)
                            if (loggedIn && isLoginPage(url) && view != null) {
                                // The full site sent us to the login: the saved session is not valid. Clear it and
                                // start the login again from the simple page (no loop: the cookie is gone).
                                loggedIn = false
                                mode = 0
                                useMode(view, 0)
                                scheduleCheck(view, 0)
                            }
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val u = request?.url?.toString()
                            if (u != null && u.contains(".mp4")) MediaSniffer.note(u) // only observed, never blocked
                            return null
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            val u = url ?: return
                            if (FbSession.loggedIn() && !isLoginPage(u) && u.contains("facebook.com")) {
                                cm.flush()
                                val onMobile = android.net.Uri.parse(u).host == "m.facebook.com"
                                loggedIn = true
                                if (onMobile && freshHome && view != null) {
                                    view.clearHistory() // Back must not return to the login pages
                                    freshHome = false
                                }
                                if (!onMobile && homeTries < 3 && view != null) {
                                    homeTries++
                                    goHome(view) // logged in from any mode: continue on the mobile site
                                } else {
                                    view?.evaluateJavascript(INJECT, null)
                                }
                            }
                        }
                    }
                    useMode(this, 0)
                    scheduleCheck(this, 0)
                }
            },
            onRelease = {
                web = null
                // The web cache only grows while browsing: empty it when leaving once it is large. The login stays.
                if (dirSize(ctx.cacheDir) > 40L * 1024 * 1024) it.clearCache(true)
                runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
                CookieManager.getInstance().flush()
                it.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.padding(8.dp).size(44.dp).clip(CircleShape).background(Color(0x99000000))
                .pointerInput(Unit) { detectTapGestures(onTap = { vm.pop() }, onLongPress = { resetLogin() }) }
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.ic_arrow_back, size = 24.dp) }
        if (!loggedIn) {
            Box(
                Modifier.align(Alignment.TopStart).padding(start = 60.dp, top = 8.dp).height(44.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Color(0x99000000))
                    .clickable {
                        web?.let { w ->
                            mode = (mode + 1) % MODES.size
                            useMode(w, mode)
                            scheduleCheck(w, mode)
                        }
                    }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Mode ${mode + 1}/${MODES.size}", style = Txt.Label.copy(color = Color.White)) }
        }
        if (dbg.isNotEmpty() && !loggedIn) {
            Text(dbg, style = Txt.Small.copy(color = Color(0x99888888)), modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp))
        }
    }
}
