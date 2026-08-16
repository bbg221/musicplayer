package com.bbg221.musicplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bbg221.musicplayer.databinding.ItemSongBinding
import com.bbg221.musicplayer.model.Song
import com.bbg221.musicplayer.util.TimeUtils

class SongAdapter(
    private val onItemClick: (Song, Int) -> Unit,
    private val onMenuClick: (Song, Int, View) -> Unit
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private val items = mutableListOf<Song>()

    fun submit(list: List<Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun songs(): List<Song> = items

    inner class VH(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = items[position]
        holder.binding.tvTitle.text = song.title
        holder.binding.tvSubtitle.text = song.artist
        holder.binding.tvDuration.text = TimeUtils.format(song.duration)
        holder.binding.root.setOnClickListener { onItemClick(song, position) }
        holder.binding.btnMore.setOnClickListener { onMenuClick(song, position, it) }
    }

    override fun getItemCount(): Int = items.size
}
