package com.personal.voyager.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("voyager", Context.MODE_PRIVATE)
    }

    fun recents(): List<String> {
        val raw = sp.getString("recents", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun addRecent(q: String) {
        val list = (listOf(q) + recents().filter { !it.equals(q, true) }).take(8)
        sp.edit().putString("recents", JSONArray(list).toString()).apply()
    }

    fun clearRecents() {
        sp.edit().remove("recents").apply()
    }

    /** Last format choice, e.g. "A:MP3_320K" or "V:720". */
    var lastChoice: String
        get() = sp.getString("last_choice", "") ?: ""
        set(v) = sp.edit().putString("last_choice", v).apply()
}
