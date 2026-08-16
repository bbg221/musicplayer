package com.bbg221.musicplayer.model

data class Playlist(
    val id: String,
    val name: String,
    val songIds: MutableList<Long> = mutableListOf()
)
