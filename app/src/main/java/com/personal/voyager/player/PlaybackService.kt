package com.personal.voyager.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Owns the Media3 session so Android shows its standard media notification while the preview plays. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val built = MediaSession.Builder(this, PlayerHub.get(this)).build()
        session = built
        // Without a MediaController binding to this service Media3 never registers the session, so no
        // notification appears. Adding it here makes the standard playback notification show up.
        addSession(built)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerHub.peek()?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
