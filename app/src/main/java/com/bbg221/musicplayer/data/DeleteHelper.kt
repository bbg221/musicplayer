package com.bbg221.musicplayer.data

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.bbg221.musicplayer.R
import com.bbg221.musicplayer.model.Song
import java.util.ArrayDeque

class DeleteHelper(private val activity: Activity, private val onDeleted: (Song) -> Unit) {

    private val pendingDeletes = ArrayDeque<Song>()

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingDeletes.isNotEmpty()) {
            onDeleted(pendingDeletes.removeFirst())
        } else {
            pendingDeletes.clear()
        }
    }

    fun delete(song: Song) {
        try {
            if (SongRepository.delete(activity, song)) {
                onDeleted(song)
            }
        } catch (e: RecoverableSecurityException) {
            pendingDeletes.addLast(song)
            val intent: IntentSender = e.userAction.actionIntent.intentSender
            launcher.launch(
                IntentSenderRequest.Builder(intent).build()
            )
        } catch (e: SecurityException) {
            Toast.makeText(activity, R.string.delete_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
