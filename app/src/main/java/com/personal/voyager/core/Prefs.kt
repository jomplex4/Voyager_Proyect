package com.personal.voyager.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

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

    /** Copied links shown on the home screen: (url, thumbnail). */
    fun clips(): List<Pair<String, String?>> {
        val raw = sp.getString("clips", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                o.getString("u") to (if (o.isNull("t")) null else o.getString("t"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveClips(list: List<Pair<String, String?>>) {
        val a = JSONArray()
        list.forEach { (u, t) -> a.put(JSONObject().put("u", u).put("t", t ?: JSONObject.NULL)) }
        sp.edit().putString("clips", a.toString()).apply()
    }

    fun dismissedClips(): List<String> {
        val raw = sp.getString("clips_dismissed", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun saveDismissed(list: List<String>) {
        sp.edit().putString("clips_dismissed", JSONArray(list).toString()).apply()
    }

    /** Last format choice, e.g. "A:MP3_320K" or "V:720". */
    var lastChoice: String
        get() = sp.getString("last_choice", "") ?: ""
        set(v) = sp.edit().putString("last_choice", v).apply()
}
