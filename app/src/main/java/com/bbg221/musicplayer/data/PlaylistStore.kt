package com.bbg221.musicplayer.data

import android.content.Context
import com.bbg221.musicplayer.model.Playlist

object PlaylistStore {
    lateinit var repo: PlaylistRepository
        private set

    fun init(context: Context) {
        if (!::repo.isInitialized) {
            repo = PlaylistRepository(context.applicationContext)
        }
    }

    fun saveAll(playlists: List<Playlist>) {
        repo.saveAll(playlists)
    }
}
