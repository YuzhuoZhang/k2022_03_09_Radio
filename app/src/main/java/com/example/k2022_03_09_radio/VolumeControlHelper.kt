import android.media.AudioManager
import android.widget.ImageButton
import android.widget.SeekBar
import com.example.k2022_03_09_radio.R

class VolumeControlHelper(private val audioManager: AudioManager) {
    private var previousVolume = 70;
    fun setupVolumeControl(volumeSeekBar: SeekBar) {
        // Set the max value of the seek bar to the maximum volume level
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxVolume

        // Initialize the seek bar progress to the current volume level
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.progress = currentVolume

        // Set up listener to handle changes in the seek bar progress
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        progress,
                        AudioManager.FLAG_PLAY_SOUND
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun setupMuteButton(muteButton: ImageButton) {

        muteButton.setOnClickListener {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVolume == 0) {
                // Unmute: Restore the volume to the previous non-zero level
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    previousVolume,
                    AudioManager.FLAG_PLAY_SOUND
                )
                muteButton.setImageResource(R.drawable.ic_unmute)
            } else {
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                // Mute: Set the volume to 0
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    0,
                    AudioManager.FLAG_PLAY_SOUND
                )
                muteButton.setImageResource(R.drawable.ic_mute)
            }
        }
    }
}
