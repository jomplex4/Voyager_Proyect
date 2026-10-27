package com.personal.voyager.dl

import com.personal.voyager.core.Names

enum class JobStatus { QUEUED, ACTIVE, PROCESSING, PAUSED, FAILED }

/**
 * choice formats: "A:M4A_128K" | "A:MP3_128K" | "A:MP3_160K" | "A:MP3_320K" | "V:720" | "F:HD" | "F:SD".
 * source: "yt" or "fb".
 */
data class DlJob(
    val id: String,
    val source: String,
    val url: String,
    val title: String,
    val thumb: String?,
    val durationSec: Long,
    val choice: String,
    val prefix: String,
    val tag: String,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0f,
    val speedBps: Long = 0L,
    val error: String? = null,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val uploader: String = "",
) {
    val isAudio: Boolean get() = choice.startsWith("A:")
    val base: String get() = Names.base(prefix, title, tag)
    val running: Boolean get() = status == JobStatus.ACTIVE || status == JobStatus.PROCESSING
}

fun tagFor(choice: String): String = when {
    choice.startsWith("A:") -> choice.substring(2)
    choice.startsWith("V:") -> choice.substring(2) + "P"
    choice == "F:SRC" || choice == "F:SNIFF" || choice.startsWith("F:Q") -> "FB"
    choice.startsWith("F:") -> choice.substring(2)
    else -> choice
}

class Src(val url: String, val size: Long, val sig: String, val ua: String)

sealed class Plan(val sources: List<Src>, val tag: String) {
    class Merge(val video: Src, val audio: Src, val webm: Boolean, tag: String) :
        Plan(listOf(video, audio), tag)

    class Direct(val src: Src, val mime: String, val ext: String, val isVideo: Boolean, tag: String, val remux: Boolean = false, val needAudio: Boolean = false) :
        Plan(listOf(src), tag)

    class M4a(val src: Src, tag: String) : Plan(listOf(src), tag)

    class Mp3(val src: Src, val kbps: Int, tag: String) : Plan(listOf(src), tag)
}
