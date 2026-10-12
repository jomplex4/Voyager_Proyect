package com.personal.voyager.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.voyager.R
import com.personal.voyager.fb.FbSession
import com.personal.voyager.vm.AppViewModel

/**
 * Facebook account: a normal Facebook login page inside the app. VOYAGER never sees the password; the login
 * stays in the WebView cookie jar and is only used to open videos that your account can already watch.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FacebookScreen(vm: AppViewModel) {
    var loggedIn by remember { mutableStateOf(FbSession.loggedIn()) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Facebook", { vm.pop() })
        if (loggedIn) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Ic(R.drawable.ic_check_circle, tint = Pal.Red, size = 56.dp)
                Spacer(Modifier.height(18.dp))
                Text("Connected to Facebook", style = Txt.Title)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Private and friends-only videos that your account can watch can now be downloaded.",
                    style = Txt.Body.copy(color = Pal.Gray),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                PillButton("Log out", {
                    FbSession.logout()
                    loggedIn = false
                    vm.say("Logged out of Facebook")
                }, Modifier.fillMaxWidth())
            }
        } else {
            AndroidView(
                factory = { c ->
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    WebView(c).apply {
                        cm.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Facebook treats the "; wv" embedded browser marker differently, so present as plain Chrome.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                check()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                check()
                            }

                            private fun check() {
                                if (FbSession.loggedIn()) {
                                    cm.flush()
                                    if (!loggedIn) {
                                        loggedIn = true
                                        vm.say("Connected to Facebook. Try the download again.")
                                        vm.pop()
                                    }
                                }
                            }
                        }
                        loadUrl("https://m.facebook.com/login")
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}
