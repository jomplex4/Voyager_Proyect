package com.personal.voyager.fb

import android.webkit.CookieManager
import android.webkit.WebStorage

/** The Facebook login lives in the WebView cookie jar; nothing is copied or stored anywhere else. */
object FbSession {
    private const val URL = "https://www.facebook.com"

    fun cookie(): String? =
        runCatching { CookieManager.getInstance().getCookie(URL) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun loggedIn(): Boolean = cookie()?.contains("c_user=") == true

    fun logout() {
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
            WebStorage.getInstance().deleteAllData()
        }
    }
}
