package com.personal.voyager.dl

import android.os.Process

/** Runs heavy work (merging, MP3 encoding) at background priority so the phone's UI and system buttons stay responsive. */
object Bg {
    inline fun <T> low(block: () -> T): T {
        val tid = Process.myTid()
        val old = Process.getThreadPriority(tid)
        Process.setThreadPriority(tid, Process.THREAD_PRIORITY_BACKGROUND)
        try {
            return block()
        } finally {
            Process.setThreadPriority(tid, old)
        }
    }
}
