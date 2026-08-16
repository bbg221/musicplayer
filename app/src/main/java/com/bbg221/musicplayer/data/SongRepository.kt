package com.bbg221.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.RecoverableSecurityException
import com.bbg221.musicplayer.model.Song

object SongRepository {

    fun scanAll(context: Context): List<Song> {
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
        try {
            context.contentResolver.query(
                collection, projection, selection, null, sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    songs.add(readSong(cursor, collection))
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return songs
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

    fun delete(context: Context, song: Song): Boolean {
        return try {
            context.contentResolver.delete(song.uri, null, null) > 0
        } catch (e: RecoverableSecurityException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        }
    }
}
