package com.bbg221.musicplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bbg221.musicplayer.databinding.ItemPlaylistBinding
import com.bbg221.musicplayer.model.Playlist

class PlaylistAdapter(
    private val onItemClick: (Playlist) -> Unit,
    private val onMenuClick: (Playlist, View) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

    private val items = mutableListOf<Playlist>()

    fun submit(list: List<Playlist>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val playlist = items[position]
        holder.binding.tvName.text = playlist.name
        holder.binding.tvCount.text = holder.itemView.context
            .getString(com.bbg221.musicplayer.R.string.playlist_songs_count, playlist.songIds.size)
        holder.binding.root.setOnClickListener { onItemClick(playlist) }
        holder.binding.btnMore.setOnClickListener { onMenuClick(playlist, it) }
    }

    override fun getItemCount(): Int = items.size
}
