package com.example.k2022_03_09_radio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.AsyncTask

class MediaPlayerTask(private val callback: MediaPlayerCallback) :
    AsyncTask<String, Void, MediaPlayer>() {

    override fun doInBackground(vararg urls: String): MediaPlayer {
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        mediaPlayer.setDataSource(urls[0])
        mediaPlayer.prepare()
        return mediaPlayer
    }

    override fun onPostExecute(result: MediaPlayer) {
        super.onPostExecute(result)
        callback.onMediaPlayerPrepared(result)
    }

    interface MediaPlayerCallback {
        fun onMediaPlayerPrepared(mediaPlayer: MediaPlayer)
    }
}
