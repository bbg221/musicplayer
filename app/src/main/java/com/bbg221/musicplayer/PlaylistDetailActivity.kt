package com.bbg221.musicplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bbg221.musicplayer.adapter.SongAdapter
import com.bbg221.musicplayer.data.PlaylistStore
import com.bbg221.musicplayer.data.SongRepository
import com.bbg221.musicplayer.databinding.ActivityPlaylistDetailBinding
import com.bbg221.musicplayer.model.Song
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlaylistDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
    }

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var adapter: SongAdapter

    private var playlistId: String = ""
    private var allSongs: List<Song> = emptyList()
    private var playlistSongs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: ""
        if (playlistId.isEmpty()) {
            finish()
            return
        }

        adapter = SongAdapter(
            onItemClick = { song, position ->
                MusicService.playQueue(this, playlistSongs, position)
                startActivity(Intent(this, PlayerActivity::class.java))
            },
            onMenuClick = { song, position, anchor -> showSongMenu(song, anchor) }
        )
        binding.recyclerSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerSongs.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlaylistMenu.setOnClickListener { showPlaylistMenu() }
        binding.fabAddSong.setOnClickListener { showAddSongsDialog() }
    }

    override fun onResume() {
        super.onResume()
        loadSongs()
    }

    private fun loadSongs() {
        val playlist = PlaylistStore.repo.loadAll().firstOrNull { it.id == playlistId }
        if (playlist == null) {
            finish()
            return
        }
        binding.tvPlaylistName.text = playlist.name
        allSongs = SongRepository.getAll(this)
        val byId = allSongs.associateBy { it.id }
        playlistSongs = playlist.songIds.mapNotNull { byId[it] }
        adapter.submit(playlistSongs)
        binding.tvEmpty.visibility = if (playlistSongs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSongMenu(song: Song, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_playlist_song, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_remove_from_playlist -> {
                        removeFromPlaylist(song)
                        true
                    }
                    R.id.menu_delete_song -> {
                        MaterialAlertDialogBuilder(this@PlaylistDetailActivity)
                            .setTitle(R.string.delete_song_title)
                            .setMessage(getString(R.string.delete_song_msg, song.title))
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                removeSongFromLibrary(song)
                            }
                            .show()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun removeFromPlaylist(song: Song) {
        val playlists = PlaylistStore.repo.loadAll()
        playlists.firstOrNull { it.id == playlistId }?.songIds?.remove(song.id)
        PlaylistStore.saveAll(playlists)
        Toast.makeText(this, R.string.removed_from_playlist, Toast.LENGTH_SHORT).show()
        loadSongs()
    }

    private fun removeFromAllPlaylists(song: Song) {
        val playlists = PlaylistStore.repo.loadAll()
        var changed = false
        for (p in playlists) {
            if (p.songIds.remove(song.id)) changed = true
        }
        if (changed) PlaylistStore.saveAll(playlists)
    }

    private fun removeSongFromLibrary(song: Song) {
        SongRepository.remove(this, song)
        removeFromAllPlaylists(song)
        Toast.makeText(this, getString(R.string.song_removed, song.title), Toast.LENGTH_SHORT).show()
        loadSongs()
    }

    private fun showPlaylistMenu() {
        PopupMenu(this, binding.btnPlaylistMenu).apply {
            menuInflater.inflate(R.menu.menu_playlist, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_delete_playlist -> {
                        val playlistName =
                            PlaylistStore.repo.loadAll().firstOrNull { it.id == playlistId }?.name
                                ?: getString(R.string.tab_playlists)
                        MaterialAlertDialogBuilder(this@PlaylistDetailActivity)
                            .setTitle(R.string.delete_playlist_title)
                            .setMessage(getString(R.string.delete_playlist_msg, playlistName))
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                val updated = PlaylistStore.repo.loadAll().toMutableList()
                                updated.removeAll { it.id == playlistId }
                                PlaylistStore.saveAll(updated)
                                finish()
                            }
                            .show()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showAddSongsDialog() {
        if (allSongs.isEmpty()) {
            allSongs = SongRepository.getAll(this)
        }
        if (allSongs.isEmpty()) {
            Toast.makeText(this, R.string.no_songs, Toast.LENGTH_SHORT).show()
            return
        }
        val playlists = PlaylistStore.repo.loadAll()
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        val available = allSongs.filter { it.id !in playlist.songIds }
        if (available.isEmpty()) {
            Toast.makeText(this, R.string.already_in_playlist, Toast.LENGTH_SHORT).show()
            return
        }
        val titles = available.map { it.title }.toTypedArray()
        val checked = BooleanArray(available.size) { false }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_songs)
            .setMultiChoiceItems(titles, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                for (i in checked.indices) {
                    if (checked[i]) {
                        playlist.songIds.add(available[i].id)
                    }
                }
                PlaylistStore.saveAll(playlists)
                loadSongs()
            }
            .show()
    }
}
