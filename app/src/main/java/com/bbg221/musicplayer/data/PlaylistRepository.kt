package com.bbg221.musicplayer.data

import android.content.Context
import com.bbg221.musicplayer.model.Playlist
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PlaylistRepository(context: Context) {

    private val file = File(context.filesDir, "playlists.json")

    fun loadAll(): MutableList<Playlist> {
        val result = mutableListOf<Playlist>()
        if (!file.exists()) return result
        return try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val ids = JSONArray(obj.optString("songIds", "[]"))
                val songIds = mutableListOf<Long>()
                for (j in 0 until ids.length()) {
                    songIds.add(ids.getLong(j))
                }
                result.add(
                    Playlist(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "未命名歌单"),
                        songIds = songIds
                    )
                )
            }
            result
        } catch (_: Exception) {
            result
        }
    }

    fun saveAll(playlists: List<Playlist>) {
        val array = JSONArray()
        for (p in playlists) {
            val ids = JSONArray()
            for (id in p.songIds) {
                ids.put(id)
            }
            val obj = JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("songIds", ids.toString())
            array.put(obj)
        }
        file.writeText(array.toString())
    }
}
