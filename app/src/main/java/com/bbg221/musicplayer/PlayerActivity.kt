package com.bbg221.musicplayer

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.bbg221.musicplayer.databinding.ActivityPlayerBinding
import com.bbg221.musicplayer.model.Song
import com.bbg221.musicplayer.util.TimeUtils

class PlayerActivity : AppCompatActivity(), MusicService.PlayerListener {

    private lateinit var binding: ActivityPlayerBinding
    private var userSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPlay.setOnClickListener { MusicService.playPause(this) }
        binding.btnNext.setOnClickListener { MusicService.next(this) }
        binding.btnPrev.setOnClickListener { MusicService.prev(this) }
        binding.ivModeIcon.setOnClickListener { MusicService.toggleMode(this) }
        binding.tvMode.setOnClickListener { MusicService.toggleMode(this) }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                val duration = MusicService.duration()
                val position = duration * (seekBar?.progress ?: 0) / 1000L
                MusicService.seekTo(this@PlayerActivity, position)
                binding.tvPosition.text = TimeUtils.format(position)
            }
        })

        updateAll()
    }

    override fun onStart() {
        super.onStart()
        MusicService.listeners.add(this)
        updateAll()
    }

    override fun onStop() {
        super.onStop()
        MusicService.listeners.remove(this)
    }

    override fun onSongChanged(song: Song?, index: Int) {
        updateSongInfo()
    }

    override fun onPlayStateChanged(playing: Boolean) {
        updatePlayButton()
    }

    override fun onModeChanged(mode: Int) {
        updateMode()
    }

    override fun onPositionChanged(position: Long, duration: Long) {
        if (userSeeking) return
        if (duration > 0) {
            binding.seekBar.progress = (position * 1000 / duration).toInt()
        }
        binding.tvPosition.text = TimeUtils.format(position)
        binding.tvDuration.text = TimeUtils.format(duration)
    }

    private fun updateAll() {
        updateSongInfo()
        updatePlayButton()
        updateMode()
        if (MusicService.currentSong() == null) {
            finish()
        }
    }

    private fun updateSongInfo() {
        val song = MusicService.currentSong()
        if (song == null) {
            finish()
            return
        }
        binding.tvTitle.text = song.title
        binding.tvArtist.text = song.artist
        binding.tvDuration.text = TimeUtils.format(song.duration)
        binding.tvPosition.text = TimeUtils.format(MusicService.position())
        binding.seekBar.progress = 0
    }

    private fun updatePlayButton() {
        binding.btnPlay.setImageResource(
            if (MusicService.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
    }

    private fun updateMode() {
        val mode = MusicService.playMode()
        val icon = if (mode == MusicService.MODE_LOOP_ONE) R.drawable.ic_repeat_one else R.drawable.ic_order
        val text =
            if (mode == MusicService.MODE_LOOP_ONE) R.string.mode_loop_one else R.string.mode_sequence
        binding.ivModeIcon.setImageResource(icon)
        binding.tvMode.setText(text)
    }
}
