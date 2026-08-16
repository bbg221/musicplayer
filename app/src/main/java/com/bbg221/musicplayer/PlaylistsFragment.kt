package com.bbg221.musicplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bbg221.musicplayer.adapter.PlaylistAdapter
import com.bbg221.musicplayer.data.PlaylistStore
import com.bbg221.musicplayer.databinding.FragmentPlaylistsBinding
import com.bbg221.musicplayer.model.Playlist
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.UUID

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PlaylistAdapter
    private var playlists: List<Playlist> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = PlaylistAdapter(
            onItemClick = { playlist ->
                startActivity(
                    Intent(requireContext(), PlaylistDetailActivity::class.java)
                        .putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.id)
                )
            },
            onMenuClick = { playlist, anchor -> showPlaylistMenu(playlist, anchor) }
        )
        binding.recyclerPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPlaylists.adapter = adapter
        binding.fabAddPlaylist.setOnClickListener { showNewPlaylistDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun refresh() {
        playlists = PlaylistStore.repo.loadAll()
        adapter.submit(playlists)
        binding.tvEmpty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showNewPlaylistDialog() {
        val input = EditText(requireContext())
        input.hint = getString(R.string.playlist_name_hint)
        val container = FrameLayout(requireContext())
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        val margin = (24 * resources.displayMetrics.density).toInt()
        params.setMargins(margin, 0, margin, 0)
        container.addView(input, params)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_playlist)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.playlist_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updated = PlaylistStore.repo.loadAll().toMutableList()
                updated.add(Playlist(id = UUID.randomUUID().toString(), name = name))
                PlaylistStore.saveAll(updated)
                refresh()
            }
            .show()
    }

    private fun showPlaylistMenu(playlist: Playlist, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_playlist, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_delete_playlist -> {
                        confirmDeletePlaylist(playlist)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun confirmDeletePlaylist(playlist: Playlist) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_playlist_title)
            .setMessage(getString(R.string.delete_playlist_msg, playlist.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val updated = PlaylistStore.repo.loadAll().toMutableList()
                updated.removeAll { it.id == playlist.id }
                PlaylistStore.saveAll(updated)
                refresh()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
