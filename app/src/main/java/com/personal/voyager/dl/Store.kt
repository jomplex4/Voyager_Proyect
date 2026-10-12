package com.personal.voyager.dl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.personal.voyager.core.Net
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class DoneItem(
    val id: String,
    val base: String,
    val title: String,
    val tag: String,
    val isAudio: Boolean,
    val durationSec: Long,
    val size: Long,
    val uri: String,
    val thumbPath: String?,
    val mime: String,
    val createdAt: Long,
)

/** Persists the download queue and the "Downloaded" registry as small JSON files. */
object Store {
    private lateinit var ctx: Context
    private val lock = Any()

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    private val queueFile get() = File(ctx.filesDir, "queue.json")
    private val libraryFile get() = File(ctx.filesDir, "registry.json")
    val thumbDir get() = File(ctx.filesDir, "thumbs").apply { mkdirs() }

    // ---- queue ----
    fun saveQueue(jobs: List<DlJob>) = synchronized(lock) {
        val a = JSONArray()
        for (j in jobs) {
            a.put(
                JSONObject()
                    .put("id", j.id).put("source", j.source).put("url", j.url).put("title", j.title)
                    .put("thumb", j.thumb ?: JSONObject.NULL).put("dur", j.durationSec)
                    .put("choice", j.choice).put("prefix", j.prefix).put("tag", j.tag)
                    .put("status", j.status.name).put("error", j.error ?: JSONObject.NULL)
                    .put("created", j.createdAt).put("up", j.uploader)
            )
        }
        runCatching { queueFile.writeText(a.toString()) }
        Unit
    }

    /** Restores jobs as PAUSED so nothing starts by itself after the process died. */
    fun loadQueue(): List<DlJob> = synchronized(lock) {
        val f = queueFile
        if (!f.exists()) return emptyList()
        runCatching {
            val a = JSONArray(f.readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val st = runCatching { JobStatus.valueOf(o.getString("status")) }.getOrDefault(JobStatus.PAUSED)
                DlJob(
                    id = o.getString("id"), source = o.getString("source"), url = o.getString("url"),
                    title = o.getString("title"), thumb = if (o.isNull("thumb")) null else o.getString("thumb"),
                    durationSec = o.optLong("dur", 0), choice = o.getString("choice"),
                    prefix = o.optString("prefix", ""), tag = o.getString("tag"),
                    status = if (st == JobStatus.FAILED) JobStatus.FAILED else JobStatus.PAUSED,
                    error = if (o.isNull("error")) null else o.getString("error"),
                    createdAt = o.optLong("created", System.currentTimeMillis()),
                    uploader = o.optString("up", ""),
                )
            }
        }.getOrDefault(emptyList())
    }

    // ---- library ----
    private val _library = MutableStateFlow<List<DoneItem>>(emptyList())
    val library: StateFlow<List<DoneItem>> = _library

    fun loadLibrary() {
        val items: List<DoneItem> = synchronized(lock) {
            val f = libraryFile
            if (!f.exists()) emptyList()
            else runCatching {
                val a = JSONArray(f.readText())
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    DoneItem(
                        id = o.getString("id"), base = o.getString("base"), title = o.getString("title"),
                        tag = o.getString("tag"), isAudio = o.getBoolean("audio"), durationSec = o.optLong("dur", 0),
                        size = o.optLong("size", 0), uri = o.getString("uri"),
                        thumbPath = if (o.isNull("thumb")) null else o.getString("thumb"),
                        mime = o.optString("mime", "video/mp4"), createdAt = o.optLong("created", 0),
                    )
                }
            }.getOrDefault(emptyList())
        }
        // Drop entries whose file no longer exists in MediaStore.
        val alive = items.filter { Storage.exists(ctx, it.uri) }
        _library.value = alive.sortedByDescending { it.createdAt }
        if (alive.size != items.size) persistLibrary()
    }

    private fun persistLibrary() = synchronized(lock) {
        val a = JSONArray()
        for (d in _library.value) {
            a.put(
                JSONObject()
                    .put("id", d.id).put("base", d.base).put("title", d.title).put("tag", d.tag)
                    .put("audio", d.isAudio).put("dur", d.durationSec).put("size", d.size).put("uri", d.uri)
                    .put("thumb", d.thumbPath ?: JSONObject.NULL).put("mime", d.mime).put("created", d.createdAt)
            )
        }
        runCatching { libraryFile.writeText(a.toString()) }
        Unit
    }

    fun addDone(item: DoneItem) {
        _library.update { listOf(item) + it.filter { x -> x.id != item.id } }
        persistLibrary()
    }

    fun removeDone(ids: Set<String>) {
        val victims = _library.value.filter { it.id in ids }
        for (v in victims) {
            Storage.delete(ctx, v.uri, v.mime)
            v.thumbPath?.let { runCatching { File(it).delete() } }
        }
        _library.update { list -> list.filter { it.id !in ids } }
        persistLibrary()
    }

    fun existsBase(base: String): Boolean =
        _library.value.any { it.base.equals(base, ignoreCase = true) } || Storage.existsBase(base)

    fun saveThumbBytes(id: String, bytes: ByteArray): String? = runCatching {
        val f = File(thumbDir, "$id.jpg")
        f.writeBytes(bytes)
        f.absolutePath
    }.getOrNull()

    /** Downloads a small JPEG copy of the thumbnail so the Play tab works offline. */
    fun saveThumb(id: String, url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val req = Request.Builder().url(url).header("User-Agent", Net.UA_DESKTOP).build()
            Net.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val bytes = r.body?.bytes() ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 320) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
                val f = File(thumbDir, "$id.jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                bmp.recycle()
                f.absolutePath
            }
        }.getOrNull()
    }
}
