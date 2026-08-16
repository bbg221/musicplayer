package com.bbg221.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bbg221.musicplayer.data.DeleteHelper
import com.bbg221.musicplayer.data.PlaylistStore
import com.bbg221.musicplayer.databinding.ActivityMainBinding
import com.bbg221.musicplayer.model.Song
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity(), MusicService.PlayerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deleteHelper: DeleteHelper

    private val songsFragment = SongsFragment()
    private val playlistsFragment = PlaylistsFragment()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val deleteResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        deleteHelper.onResult(result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PlaylistStore.init(this)
        deleteHelper = DeleteHelper(this, deleteResultLauncher) { song ->
            onSongDeleted(song)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_songs -> showFragment(songsFragment, "songs")
                R.id.menu_playlists -> showFragment(playlistsFragment, "playlists")
            }
            true
        }
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.menu_songs
        }
        setupMiniPlayer()
        requestPermissionsIfNeeded()
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        val target = existing ?: fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, target, tag)
            .commit()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT <= 32) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener {
            if (MusicService.currentSong() != null) {
                startActivity(Intent(this, PlayerActivity::class.java))
            }
        }
        binding.btnMiniPlay.setOnClickListener {
            MusicService.playPause(this)
        }
        binding.btnMiniNext.setOnClickListener {
            MusicService.next(this)
        }
    }

    fun confirmDeleteSong(song: Song) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_song_title)
            .setMessage(getString(R.string.delete_song_msg, song.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteHelper.delete(song)
            }
            .show()
    }

    private fun onSongDeleted(song: Song) {
        val playlists = PlaylistStore.repo.loadAll()
        var changed = false
        for (p in playlists) {
            if (p.songIds.remove(song.id)) changed = true
        }
        if (changed) PlaylistStore.saveAll(playlists)
        Toast.makeText(this, getString(R.string.song_deleted, song.title), Toast.LENGTH_SHORT).show()
        val frag = supportFragmentManager.findFragmentByTag("songs")
        if (frag is SongsFragment) frag.refresh()
        val pf = supportFragmentManager.findFragmentByTag("playlists")
        if (pf is PlaylistsFragment) pf.refresh()
    }

    override fun onStart() {
        super.onStart()
        MusicService.listeners.add(this)
        updateMiniPlayer()
    }

    override fun onStop() {
        super.onStop()
        MusicService.listeners.remove(this)
    }

    override fun onSongChanged(song: Song?, index: Int) {
        updateMiniPlayer()
    }

    override fun onPlayStateChanged(playing: Boolean) {
        updateMiniPlayer()
    }

    override fun onModeChanged(mode: Int) {
        updateMiniPlayer()
    }

    private fun updateMiniPlayer() {
        val song = MusicService.currentSong()
        if (song == null) {
            binding.miniPlayer.visibility = android.view.View.GONE
            return
        }
        binding.miniPlayer.visibility = android.view.View.VISIBLE
        binding.miniTitle.text = song.title
        binding.miniSubtitle.text = song.artist
        binding.btnMiniPlay.setImageResource(
            if (MusicService.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
    }
}
