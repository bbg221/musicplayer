package com.bbg221.musicplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bbg221.musicplayer.adapter.SongAdapter
import com.bbg221.musicplayer.data.PlaylistStore
import com.bbg221.musicplayer.data.SongRepository
import com.bbg221.musicplayer.databinding.FragmentSongsBinding
import com.bbg221.musicplayer.model.Song
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SongAdapter
    private var allSongs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SongAdapter(
            onItemClick = { song, position ->
                MusicService.playQueue(requireContext(), allSongs, position)
                startActivity(Intent(requireContext(), PlayerActivity::class.java))
            },
            onMenuClick = { song, position, anchor ->
                showSongMenu(song, anchor)
            }
        )
        binding.recyclerSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSongs.adapter = adapter
        binding.btnScan.setOnClickListener {
            refresh(forceScan = true)
            Toast.makeText(
                requireContext(),
                getString(R.string.scan_done, allSongs.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun refresh(forceScan: Boolean = false) {
        val context = requireContext()
        allSongs = if (forceScan) {
            SongRepository.scanAll(context)?.also { SongRepository.save(context, it) } ?: emptyList()
        } else {
            SongRepository.getAll(context)
        }
        adapter.submit(allSongs)
        binding.tvSongCount.text = getString(R.string.song_count, allSongs.size)
        binding.tvEmpty.visibility =
            if (allSongs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSongMenu(song: Song, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_song, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_add_to_playlist -> {
                        showAddToPlaylistDialog(song)
                        true
                    }
                    R.id.menu_delete_song -> {
                        (requireActivity() as MainActivity).confirmDeleteSong(song)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val playlists = PlaylistStore.repo.loadAll()
        if (playlists.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_playlist_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val names = playlists.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_playlist)
            .setItems(names) { _, which ->
                val playlist = playlists[which]
                if (playlist.songIds.contains(song.id)) {
                    Toast.makeText(requireContext(), R.string.already_in_playlist, Toast.LENGTH_SHORT).show()
                } else {
                    playlist.songIds.add(song.id)
                    PlaylistStore.saveAll(playlists)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.added_to_playlist, playlist.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
