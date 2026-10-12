package com.personal.voyager.dl

import android.app.Application
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
)

/** Process-wide download queue. Max 2 concurrent jobs, the rest wait. */
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

    private fun tmpDir(): File {
        val base = app.getExternalFilesDir("tmp") ?: File(app.filesDir, "tmp")
        base.mkdirs()
        return base
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
                tag = tagFor(s.choice),
            )
            val exists = Store.existsBase(job.base) ||
                _jobs.value.any { it.base.equals(job.base, true) } ||
                fresh.any { it.base.equals(job.base, true) } ||
                Saver.existsInStore(app, job.base, job.isAudio)
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

    fun pause(id: String) {
        update(id, true) { if (it.status == JobStatus.FAILED) it else it.copy(status = JobStatus.PAUSED, speedBps = 0) }
        synchronized(lock) { running.remove(id) }?.cancel()
        pump()
    }

    fun pauseAll() {
        _jobs.value.filter { it.status != JobStatus.FAILED }.forEach { pause(it.id) }
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
            var slots = MAX_CONCURRENT - _jobs.value.count { it.running }
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
            tmpDir().listFiles()?.filter { it.name.startsWith("$id.") }?.forEach { it.delete() }
        }
    }

    // ---------------- job pipeline ----------------

    private class Meter(val job: String) {
        var lastT = System.currentTimeMillis()
        var lastB = 0L
        var lastPush = 0L
    }

    private suspend fun runJob(id: String) {
        val tmp = tmpDir()
        try {
            val job = get(id) ?: return
            var plan = Resolver.resolve(job)
            try {
                execute(job, plan, tmp)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // WebM merge can fail on some devices: redo once with H.264 at 1080p or below.
                if (plan is Plan.Merge && plan.webm && e !is java.io.IOException) {
                    toast("This quality could not be merged here, using H.264 instead")
                    cleanParts(id)
                    plan = Resolver.resolve(job, allowWebm = false)
                    execute(job, plan, tmp)
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

    private suspend fun execute(job: DlJob, plan: Plan, tmp: File) {
        val id = job.id
        val ctx = currentCoroutineContext()
        val check = { ctx.ensureActive() }

        val expected = plan.sources.sumOf { it.size.coerceAtLeast(0) }
        val estFinal = if (expected > 0) expected else 20L * 1024 * 1024
        val free = StatFs(tmp.absolutePath).availableBytes
        val needed = when (plan) {
            is Plan.Merge -> (estFinal * 2.3).toLong()
            is Plan.Mp3 -> estFinal * 2
            else -> (estFinal * 2.1).toLong()
        }
        if (free < needed) throw java.io.IOException("Not enough free space on the phone")

        update(id) { it.copy(tag = plan.tag, total = expected) }

        val files = plan.sources.indices.map { File(tmp, "$id.$it.part") }
        val meter = Meter(id)

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

        // 1) download every source
        for (i in plan.sources.indices) {
            val src = plan.sources[i]
            val sigFile = File(tmp, "$id.$i.sig")
            if (sigFile.exists() && sigFile.readText() != src.sig) files[i].delete()
            sigFile.writeText(src.sig)
            val fetcher = Fetcher(src) {
                val fresh = Resolver.resolve(job, allowWebm = !(plan is Plan.Merge && !plan.webm))
                fresh.sources[i]
            }
            fetcher.into(files[i]) { tick() }
        }
        check()

        // 2) process
        update(id) { it.copy(status = JobStatus.PROCESSING, progress = 0.95f, speedBps = 0, downloaded = files.sumOf { f -> f.length() }) }
        val out: File
        val ext: String
        val mime: String
        val audio: Boolean
        fun proc(p: Float) = update(id) { it.copy(progress = (0.95f + 0.05f * p).coerceIn(0.95f, 0.999f)) }

        when (plan) {
            is Plan.Merge -> {
                ext = if (plan.webm) "webm" else "mp4"
                mime = if (plan.webm) "video/webm" else "video/mp4"
                audio = false
                out = File(tmp, "$id.out.$ext")
                withContext(Dispatchers.IO) {
                    Mux.mergeVideoAudio(files[0].absolutePath, files[1].absolutePath, out.absolutePath, plan.webm, check) { proc(it) }
                }
            }
            is Plan.Direct -> {
                ext = plan.ext
                mime = plan.mime
                audio = false
                out = files[0]
            }
            is Plan.M4a -> {
                ext = "m4a"
                mime = "audio/mp4"
                audio = true
                out = File(tmp, "$id.out.m4a")
                withContext(Dispatchers.IO) {
                    Mux.remuxAudioToM4a(files[0].absolutePath, out.absolutePath, check) { proc(it) }
                }
            }
            is Plan.Mp3 -> {
                ext = "mp3"
                mime = "audio/mpeg"
                audio = true
                out = File(tmp, "$id.out.mp3")
                withContext(Dispatchers.Default) {
                    Mp3.encode(files[0].absolutePath, out.absolutePath, plan.kbps, check) { proc(it) }
                }
            }
        }
        check()

        // 3) save to MediaStore + registry
        val fresh = get(id) ?: job
        val base = Names.base(fresh.prefix, fresh.title, plan.tag)
        val finalName = "$base.$ext"
        val uri: Uri = withContext(Dispatchers.IO) { Saver.save(app, out, finalName, mime, audio) }
        val size = out.length()
        val thumb = withContext(Dispatchers.IO) { Store.saveThumb(id, job.thumb) }
        Store.addDone(
            DoneItem(
                id = id, base = base, title = job.title, tag = plan.tag, isAudio = audio,
                durationSec = job.durationSec, size = size, uri = uri.toString(), thumbPath = thumb,
                mime = mime, createdAt = System.currentTimeMillis(),
            )
        )
        cleanParts(id)
        _jobs.update { list -> list.filter { it.id != id } }
        persist()
    }
}
