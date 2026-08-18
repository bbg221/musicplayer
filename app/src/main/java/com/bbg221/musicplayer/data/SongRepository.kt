package com.bbg221.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.bbg221.musicplayer.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SongRepository {

    const val MIN_DURATION_MS = 30_000L

    private const val FILE_NAME = "songs.json"

    private fun cacheFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun isScanned(context: Context): Boolean = cacheFile(context).exists()

    fun getAll(context: Context): List<Song> {
        if (isScanned(context)) return loadCached(context)
        val songs = scanAll(context) ?: return emptyList()
        save(context, songs)
        return songs
    }

    fun scanAll(context: Context): List<Song>? {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        return try {
            context.contentResolver.query(
                collection, projection, selection, null, sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val song = readSong(cursor, collection)
                    if (song.duration < MIN_DURATION_MS) continue
                    songs.add(song)
                }
            }
            songs
        } catch (_: SecurityException) {
            null
        }
    }

    private fun readSong(cursor: Cursor, collection: Uri): Song {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
            ?: "未知歌曲"
        val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
            ?: "未知艺术家"
        val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
            ?: "未知专辑"
        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
        val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            albumId = albumId,
            size = size,
            uri = ContentUris.withAppendedId(collection, id)
        )
    }

    fun save(context: Context, songs: List<Song>) {
        val array = JSONArray()
        for (s in songs) {
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("artist", s.artist)
                    .put("album", s.album)
                    .put("duration", s.duration)
                    .put("albumId", s.albumId)
                    .put("size", s.size)
            )
        }
        cacheFile(context).writeText(array.toString())
    }

    fun loadCached(context: Context): List<Song> {
        val file = cacheFile(context)
        if (!file.exists()) return emptyList()
        val result = mutableListOf<Song>()
        return try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                result.add(
                    Song(
                        id = id,
                        title = obj.optString("title", "未知歌曲"),
                        artist = obj.optString("artist", "未知艺术家"),
                        album = obj.optString("album", "未知专辑"),
                        duration = obj.optLong("duration"),
                        albumId = obj.optLong("albumId"),
                        size = obj.optLong("size"),
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        )
                    )
                )
            }
            result
        } catch (_: Exception) {
            file.delete()
            emptyList()
        }
    }

    fun remove(context: Context, song: Song) {
        val songs = loadCached(context).toMutableList()
        if (songs.removeAll { it.id == song.id }) {
            save(context, songs)
        }
    }
}
