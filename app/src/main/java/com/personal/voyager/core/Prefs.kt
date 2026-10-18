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

    class ClipRec(val url: String, val thumb: String?, val at: Long, val key: String)

    /** Copied links shown on the home screen. */
    fun clips(): List<ClipRec> {
        val raw = sp.getString("clips", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                ClipRec(
                    o.getString("u"), if (o.isNull("t")) null else o.getString("t"),
                    o.optLong("a", 0L), o.optString("k", o.getString("u")),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveClips(list: List<ClipRec>) {
        val a = JSONArray()
        list.forEach { c ->
            a.put(JSONObject().put("u", c.url).put("t", c.thumb ?: JSONObject.NULL).put("a", c.at).put("k", c.key))
        }
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

    /** Where a long video (over 10 minutes) was left, so it can continue from there. */
    fun resumeGet(id: String): Long = runCatching {
        JSONObject(sp.getString("resume", "{}") ?: "{}").optLong(id, 0L)
    }.getOrDefault(0L)

    fun resumeSet(id: String, posMs: Long) {
        runCatching {
            val o = JSONObject(sp.getString("resume", "{}") ?: "{}")
            o.remove(id)
            o.put(id, posMs)
            while (o.length() > 60) o.remove(o.keys().next())
            sp.edit().putString("resume", o.toString()).apply()
        }
    }

    fun resumeClear(id: String) {
        runCatching {
            val o = JSONObject(sp.getString("resume", "{}") ?: "{}")
            if (o.has(id)) {
                o.remove(id)
                sp.edit().putString("resume", o.toString()).apply()
            }
        }
    }

    /** True once the notification permission dialog was shown (only needed on Android 13+). */
    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) = sp.edit().putBoolean("asked_notif", v).apply()

    /** Last format choice, e.g. "A:MP3_320K" or "V:720". */
    var lastChoice: String
        get() = sp.getString("last_choice", "") ?: ""
        set(v) = sp.edit().putString("last_choice", v).apply()
}
