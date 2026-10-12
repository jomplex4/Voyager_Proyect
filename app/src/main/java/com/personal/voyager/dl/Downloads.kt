package com.personal.voyager.dl

import android.app.Application
import android.content.Intent
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.personal.voyager.core.Names
import com.personal.voyager.yt.YtRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class Spec(
    val source: String,
    val url: String,
    val title: String,
    val thumb: String?,
    val durationSec: Long,
    val choice: String,
    val prefix: String = "",
    val uploader: String = "",
)

/** Process-wide download queue. Max 2 jobs DOWNLOADING at once; any number can be processing. */
object Downloads {
    private const val MAX_CONCURRENT = 2

    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val running = HashMap<String, Job>()

    private val _jobs = MutableStateFlow<List<DlJob>>(emptyList())
    val jobs: StateFlow<List<DlJob>> = _jobs

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    val libraryReady = CompletableDeferred<Unit>()

    fun init(application: Application) {
        app = application
        Store.init(application)
        _jobs.value = Store.loadQueue()
        scope.launch(Dispatchers.IO) {
            try {
                Store.loadLibrary()
            } finally {
                libraryReady.complete(Unit)
            }
        }
    }

    private fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun persist() = Store.saveQueue(_jobs.value)

    private fun update(id: String, save: Boolean = false, f: (DlJob) -> DlJob) {
        _jobs.update { list -> list.map { if (it.id == id) f(it) else it } }
        if (save) persist()
    }

    private fun get(id: String): DlJob? = _jobs.value.firstOrNull { it.id == id }

    // ---------------- public API ----------------

    /** Returns (added, duplicates). */
    fun enqueue(specs: List<Spec>): Pair<Int, Int> {
        var added = 0
        var dup = 0
        val fresh = ArrayList<DlJob>()
        for (s in specs) {
            val job = DlJob(
                id = UUID.randomUUID().toString(), source = s.source, url = s.url, title = s.title,
                thumb = s.thumb, durationSec = s.durationSec, choice = s.choice, prefix = s.prefix,
                tag = tagFor(s.choice), uploader = s.uploader,
            )
            val exists = Store.existsBase(job.base) ||
                _jobs.value.any { it.base.equals(job.base, true) } ||
                fresh.any { it.base.equals(job.base, true) }
            if (exists) dup++ else {
                fresh.add(job)
                added++
            }
        }
        if (fresh.isNotEmpty()) {
            _jobs.update { it + fresh }
            persist()
            ensureService()
            pump()
        }
        return added to dup
    }

    /** A job that is already merging/encoding cannot be paused: pausing would redo that work. */
    fun pause(id: String) {
        val j = get(id) ?: return
        if (j.status == JobStatus.PROCESSING || j.status == JobStatus.FAILED) return
        update(id, true) { it.copy(status = JobStatus.PAUSED, speedBps = 0) }
        synchronized(lock) { running.remove(id) }?.cancel()
        pump()
    }

    fun pauseAll() {
        _jobs.value.forEach { pause(it.id) }
    }

    fun resume(id: String) {
        update(id, true) { it.copy(status = JobStatus.QUEUED, error = null) }
        ensureService()
        pump()
    }

    fun resumeAll() {
        _jobs.value.filter { it.status == JobStatus.PAUSED }.forEach { update(it.id) { j -> j.copy(status = JobStatus.QUEUED) } }
        persist()
        ensureService()
        pump()
    }

    fun retry(id: String) = resume(id)

    fun remove(ids: Set<String>) {
        for (id in ids) {
            synchronized(lock) { running.remove(id) }?.cancel()
            cleanParts(id)
        }
        _jobs.update { list -> list.filter { it.id !in ids } }
        persist()
        pump()
    }

    fun hasWork(): Boolean = _jobs.value.any { it.status == JobStatus.QUEUED || it.running }

    private fun ensureService() {
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        }
    }

    // ---------------- scheduler ----------------

    private fun pump() {
        synchronized(lock) {
            // Only jobs that are downloading count toward the limit; merging/encoding jobs never block the queue.
            var slots = MAX_CONCURRENT - _jobs.value.count { it.status == JobStatus.ACTIVE }
            for (j in _jobs.value) {
                if (slots <= 0) break
                if (j.status == JobStatus.QUEUED && !running.containsKey(j.id)) {
                    update(j.id, true) { it.copy(status = JobStatus.ACTIVE, progress = 0f, speedBps = 0, error = null) }
                    running[j.id] = scope.launch {
                        try {
                            runJob(j.id)
                        } finally {
                            synchronized(lock) { running.remove(j.id) }
                            pump()
                        }
                    }
                    slots--
                }
            }
        }
    }

    private fun cleanParts(id: String) {
        runCatching {
            Storage.tmp().listFiles()?.filter { it.name.startsWith("$id.") }?.forEach { it.delete() }
        }
    }

    // ---------------- job pipeline ----------------

    private class Meter {
        var lastT = System.currentTimeMillis()
        var lastB = 0L
        var lastPush = 0L
    }

    private suspend fun runJob(id: String) {
        try {
            val job = get(id) ?: return
            var plan = Resolver.resolve(job)
            try {
                execute(job, plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // WebM merge can fail on some devices: redo once with H.264 at 1080p or below.
                if (plan is Plan.Merge && plan.webm && e !is java.io.IOException) {
                    toast("This quality could not be merged here, using H.264 instead")
                    cleanParts(id)
                    plan = Resolver.resolve(job, allowWebm = false)
                    execute(job, plan)
                } else throw e
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val msg = if (e is java.io.IOException && e.message?.contains("space", true) == true) e.message
            else YtRepo.friendly(e)
            update(id, true) { it.copy(status = JobStatus.FAILED, error = msg, speedBps = 0) }
        }
    }

    private fun cleanArtist(s: String): String =
        s.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("VEVO$", RegexOption.IGNORE_CASE), "").trim()

    private suspend fun execute(job: DlJob, plan: Plan) {
        val id = job.id
        val ctx = currentCoroutineContext()
        val check = { ctx.ensureActive() }

        Storage.prepare()
        val tmp = Storage.tmp()
        val expected = plan.sources.sumOf { it.size.coerceAtLeast(0) }
        val estFinal = if (expected > 0) expected else 20L * 1024 * 1024
        val free = StatFs(tmp.absolutePath).availableBytes
        val needed = when (plan) {
            is Plan.Merge -> (estFinal * 2.2).toLong()
            is Plan.Mp3 -> estFinal * 2
            else -> (estFinal * 2.1).toLong()
        }
        if (free < needed) throw java.io.IOException("Not enough free space on the phone")

        update(id) { it.copy(tag = plan.tag, total = expected) }

        val files = plan.sources.indices.map { File(tmp, "$id.$it.part") }
        val meter = Meter()

        fun tick() {
            val now = System.currentTimeMillis()
            if (now - meter.lastPush < 400) return
            val have = files.sumOf { if (it.exists()) it.length() else 0L }
            val dt = now - meter.lastT
            var speed = -1L
            if (dt >= 1000) {
                speed = ((have - meter.lastB) * 1000 / dt).coerceAtLeast(0)
                meter.lastT = now
                meter.lastB = have
            }
            meter.lastPush = now
            val frac = if (expected > 0) (have.toDouble() / expected).coerceIn(0.0, 1.0) else 0.0
            update(id) { it.copy(downloaded = have, progress = (frac * 0.95).toFloat(), speedBps = if (speed >= 0) speed else it.speedBps) }
        }

        val isAudio = plan is Plan.M4a || plan is Plan.Mp3
        val out: File
        val ext: String
        val mime: String
        var cover: Cover.Result? = null

        coroutineScope {
            val coverJob = if (isAudio) async(Dispatchers.IO) { Cover.fetch(job) } else null

            // 1) every source downloads at the same time (video + audio in parallel)
            plan.sources.indices.map { i ->
                launch(Dispatchers.IO) {
                    val src = plan.sources[i]
                    val sigFile = File(tmp, "$id.$i.sig")
                    if (sigFile.exists() && sigFile.readText() != src.sig) files[i].delete()
                    sigFile.writeText(src.sig)
                    Fetcher(src) {
                        Resolver.resolve(job, allowWebm = !(plan is Plan.Merge && !plan.webm), fresh = true).sources[i]
                    }.into(files[i]) { tick() }
                }
            }.joinAll()
            check()
            cover = coverJob?.await()
        }

        // 2) process (no pause while this runs)
        update(id) { it.copy(status = JobStatus.PROCESSING, progress = 0.95f, speedBps = 0, downloaded = files.sumOf { f -> f.length() }) }
        pump() // this job no longer uses a download slot: the next waiting one can start now
        fun proc(p: Float) = update(id) { it.copy(progress = (0.95f + 0.05f * p).coerceIn(0.95f, 0.999f)) }

        val title = job.title
        val artist = cleanArtist(job.uploader)
        val album = artist.ifEmpty { "Voyager" }

        when (plan) {
            is Plan.Merge -> {
                ext = if (plan.webm) "webm" else "mp4"
                mime = if (plan.webm) "video/webm" else "video/mp4"
                out = File(tmp, "$id.out.$ext")
                withContext(Dispatchers.IO) {
                    Mux.mergeVideoAudio(files[0].absolutePath, files[1].absolutePath, out.absolutePath, plan.webm, check) { proc(it) }
                }
            }
            is Plan.Direct -> {
                ext = plan.ext
                mime = plan.mime
                out = files[0]
            }
            is Plan.M4a -> {
                ext = "m4a"
                mime = "audio/mp4"
                out = File(tmp, "$id.out.m4a")
                withContext(Dispatchers.IO) {
                    Mux.remuxAudioToM4a(files[0].absolutePath, out.absolutePath, check) { proc(it) }
                    Tags.mp4(out, title, artist, album, cover?.tag)
                }
            }
            is Plan.Mp3 -> {
                ext = "mp3"
                mime = "audio/mpeg"
                out = File(tmp, "$id.out.mp3")
                val header = Tags.id3(title, artist, album, cover?.tag)
                withContext(Dispatchers.Default) {
                    Mp3.encode(files[0].absolutePath, out.absolutePath, plan.kbps, header, check) { proc(it) }
                }
            }
        }
        check()

        // 3) move into the Voyager folder (a rename, instant) and register
        val fresh = get(id) ?: job
        val base = Names.base(fresh.prefix, fresh.title, plan.tag)
        val dest = withContext(Dispatchers.IO) { Storage.move(out, "$base.$ext") }
        Storage.scan(app, dest, mime)
        val thumb = withContext(Dispatchers.IO) {
            val c = cover
            if (isAudio && c != null) Store.saveThumbBytes(id, c.thumb) else Store.saveThumb(id, job.thumb)
        }
        Store.addDone(
            DoneItem(
                id = id, base = base, title = job.title, tag = plan.tag, isAudio = isAudio,
                durationSec = job.durationSec, size = dest.length(), uri = dest.absolutePath, thumbPath = thumb,
                mime = mime, createdAt = System.currentTimeMillis(),
            )
        )
        cleanParts(id)
        _jobs.update { list -> list.filter { it.id != id } }
        persist()
    }
}
