package com.personal.voyager.core

sealed class Item {
    abstract val url: String
    abstract val title: String
    abstract val thumb: String?

    data class Video(
        override val url: String,
        override val title: String,
        override val thumb: String?,
        val uploader: String,
        val uploaderUrl: String?,
        val durationSec: Long,
        val views: Long,
        val dateText: String?,
        val live: Boolean,
    ) : Item()

    data class Channel(
        override val url: String,
        override val title: String,
        override val thumb: String?,
        val subs: Long,
    ) : Item()

    data class Playlist(
        override val url: String,
        override val title: String,
        override val thumb: String?,
        val uploader: String?,
        val count: Long,
    ) : Item()
}
