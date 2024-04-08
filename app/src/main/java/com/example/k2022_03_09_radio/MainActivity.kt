package com.example.k2022_03_09_radio

import VolumeControlHelper
import android.content.ContentValues.TAG
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.nfc.tech.MifareUltralight.PAGE_SIZE
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.*
import java.io.IOException


class MainActivity : AppCompatActivity() {


    //Layouts
    private lateinit var currentStationLayout: RelativeLayout
    private lateinit var videoLayout: RelativeLayout
    private lateinit var stationListLayout: RelativeLayout

    //views
    private lateinit var recyclerView: RecyclerView
    private lateinit var videoView: VideoView
    private lateinit var stationImageView: ImageView
    private lateinit var stationNameTextView: TextView
    private lateinit var topNavigation: BottomNavigationView

    //Buttons
    private lateinit var playPauseButton: ImageButton
    private lateinit var backButtonVideo: ImageButton
    private lateinit var muteButton: ImageButton

    //Volume Controller
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeControlHelper: VolumeControlHelper
    private lateinit var audioManager: AudioManager

    //flags
    private var isVideoPlaying = false
    private var isVideoPaused = false
    private var isLoading = false
    private var radioOn: Boolean = false

    //Station parameters
    private lateinit var TOTAL_RADIO_STATIONS: List<RadioStation>
    private lateinit var TOTAL_VIDEO_STATIONS: List<VideoStation>
    private lateinit var station_list: List<ViewModel>;
    private var currentStation: ViewModel? = null
    private var currentModelView: Any? = null
    private lateinit var stationAdapter: StationAdapter

    private lateinit var radioModelViews: List<RadioModelView>
    private lateinit var videoModelViews: List<VideoModelView>

    //Mediaplayer
    private var mediaPlayerJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private var currentPage = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        muteButton = findViewById(R.id.muteButton)
        currentStationLayout = findViewById(R.id.currentStationLayout)
        videoLayout = findViewById(R.id.videoLayout)
        stationImageView = findViewById(R.id.stationImageView)
        stationNameTextView = findViewById(R.id.stationNameTextView)
        playPauseButton = findViewById(R.id.playPauseButton)
        backButtonVideo = findViewById(R.id.backButton)
        recyclerView = findViewById(R.id.recyclerView)
        videoView = findViewById(R.id.videoView)
        topNavigation = findViewById<BottomNavigationView>(R.id.topNavigation)
        stationListLayout = findViewById<RelativeLayout>(R.id.stationList)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        volumeControlHelper = VolumeControlHelper(audioManager)
        recyclerView.layoutManager = LinearLayoutManager(this)


        // Initialize the mute button based on the mute status
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val isMuted = currentVolume == 0
        updateMuteButton(isMuted)

        TOTAL_RADIO_STATIONS = listOf(
            RadioStation("WHUS FM", "http://stream.whus.org:8000/whusfm", R.drawable.radiostation),
            RadioStation(
                "FREEDOM FM",
                "https://edge3.audioxi.com/FREEDOMFM",
                R.drawable.radiostation
            ),
            RadioStation(
                "HIT RADIO",
                "http://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3",
                R.drawable.radiostation
            ),
            RadioStation(
                "WORLDWIDE FM",
                "https://worldwidefm.out.airtime.pro/worldwidefm_a",
                R.drawable.radiostation
            ),

            )
        TOTAL_VIDEO_STATIONS = listOf(
            VideoStation(
                "Minons",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                R.drawable.minions
            ),

            )
        // Initialize radio and video ModelViews
        radioModelViews = TOTAL_RADIO_STATIONS.map { station ->
            RadioModelView(station)
        }

        videoModelViews = TOTAL_VIDEO_STATIONS.map { station ->
            VideoModelView(station)
        }
        station_list = radioModelViews

        //Initialize and setting the mediaController for the videoView
        val mediaController = MediaController(this@MainActivity)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        stationAdapter = StationAdapter(mutableListOf()) { station ->
            if (radioOn && station is RadioModelView) {
                mediaPlayer?.stop()
                mediaPlayer?.reset()
                currentStationLayout.visibility = View.GONE
                radioOn = false
            }

            playModelView(station)
            currentStation = station
            if (station is RadioModelView) {
                updateCurrentStationUI()
            }

            volumeControlHelper.setupVolumeControl(volumeSeekBar)
            volumeControlHelper.setupMuteButton(muteButton)
        }

        recyclerView.adapter = stationAdapter
//Setting the buttons listeners
        backButtonVideo.setOnClickListener {
            toggleModelView(ModelView.VIDEO)
        }
        playPauseButton.setOnClickListener {
            if (!radioOn) {
                currentStation?.let {
                    playModelView(it)
                    updateCurrentStationUI()
                }
            } else {
                if (mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    playPauseButton.setImageResource(R.drawable.ic_play)
                } else if (mediaPlayer != null) {
                    mediaPlayer?.start()
                    playPauseButton.setImageResource(R.drawable.ic_pause)
                }
                radioOn = !radioOn
            }

        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && firstVisibleItemPosition + visibleItemCount >= totalItemCount) {
                    loadMoreItems()
                }
            }
        })

        topNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.radioMenuItem -> {
                    toggleModelView(ModelView.RADIO)
                    return@setOnNavigationItemSelectedListener true
                }
                R.id.videoMenuItem -> {
                    toggleModelView(ModelView.VIDEO)
                    return@setOnNavigationItemSelectedListener true
                }
                else -> false
            }
        }

        loadMoreItems()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val decorView: View = window.decorView
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }


    private fun loadMoreItems() {
        // Simulated API call or data fetch
        val newItems = fetchDataFromApi(currentPage)
        currentPage++

        stationAdapter.addItems(newItems)
        stationAdapter.setLoading(false)
    }

    private fun loadItems() {
        // Simulated API call or data fetch
        val newItems = fetchDataFromApi(0)
        stationAdapter.clearItems()

        stationAdapter.addItems(newItems)
        stationAdapter.setLoading(false)
    }

    private fun fetchDataFromApi(page: Int): List<ViewModel> {
        val items = mutableListOf<ViewModel>()


        val startIndex = page * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, station_list.size)

        for (i in startIndex until endIndex) {
            items.add(station_list.get(i))
        }

        return items
    }


    private fun updateCurrentStationUI() {
        currentStation?.let {
            stationNameTextView.text = it.station.name
            stationImageView.setImageResource(it.station.imageResource)
            currentStationLayout.visibility = View.VISIBLE
            playPauseButton.setImageResource(R.drawable.ic_pause)
        }
    }


    // Function to update the mute button icon based on mute status
    private fun updateMuteButton(isMuted: Boolean) {
        if (isMuted) {
            muteButton.setImageResource(R.drawable.ic_mute)
        } else {
            muteButton.setImageResource(R.drawable.ic_unmute)
        }
    }

    private fun playModelView(modelView: ViewModel) {
        mediaPlayerJob?.cancel()
        mediaPlayerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    if (modelView is RadioModelView) {
                        // stopVideoPlayback()
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(modelView.station.url)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            prepareAsync()
                        }

                        mediaPlayer?.setOnPreparedListener { player ->
                            player.start()
                            radioOn = true
                        }

                        mediaPlayer?.setOnCompletionListener {
                            it.release()
                            radioOn = false
                        }
                    } else if (modelView is VideoModelView) {
                        //currentStationLayout.visibility = View.GONE

                        stationListLayout.visibility = View.GONE
                        videoLayout.visibility = View.VISIBLE
                        // stopAudioPlayback()
                        //  mediaPlayer?.release()
                        //  val videoView = findViewById<VideoView>(R.id.videoView)
                        if (isVideoPlaying) {
                            videoView.pause()
                            isVideoPaused = true;

                        } else if (!isVideoPaused) {
                            val videoPath =
                                "android.resource://" + packageName + "/" + R.raw.minions
                            videoView.setVideoPath(videoPath)
                            videoView.start()
                            isVideoPaused = false;

                        } else {
                            videoView.start()
                        }
                        isVideoPlaying = !isVideoPlaying

                    } else {

                    }
                }
                currentModelView = modelView
            } catch (e: IOException) {
                Log.e(TAG, "Error playing media station: ${e.message}")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Illegal state error during media playback: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error during media playback: ${e.message}")
            }
        }
    }

    fun toggleLayoutVisibility(view: View) {
        val layout = findViewById<RelativeLayout>(R.id.currentStationLayout)
        if (layout.visibility == View.VISIBLE) {
            // Hide the layout
            layout.visibility = View.GONE
        } else {
            // Show the layout
            layout.visibility = View.VISIBLE
        }
    }

    private fun stopAudioPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.reset()
    }

    private fun stopVideoPlayback() {
        val videoView = findViewById<VideoView>(R.id.videoView)
        videoView.stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioPlayback()
        stopVideoPlayback()
        mediaPlayerJob?.cancel()
    }

    private var viewModel: Any? = null
    private fun toggleModelView(modelView: Any) {
        when (modelView) {
            ModelView.RADIO -> {
                currentStationLayout.visibility = View.VISIBLE
                videoLayout.visibility = View.GONE
                station_list = radioModelViews

            }
            ModelView.VIDEO -> {
                //  currentStationLayout.visibility = View.GONE
                videoLayout.visibility = View.GONE
                stationListLayout.visibility = View.VISIBLE
                station_list = videoModelViews

            }

        }
        loadItems()
        currentModelView = modelView
        /*  if (currentModelView != modelView) {
              playModelView(modelView)
          }*/
    }
}

enum class ModelView {
    RADIO,
    VIDEO
}

// RadioModelView.kt
class RadioModelView(override val station: RadioStation) : ViewModel(station) {

    // You can add more properties or methods specific to radio stations here
}

// VideoModelView.kt
class VideoModelView(override val station: VideoStation) : ViewModel(station) {
    // You can add more properties or methods specific to video stations here
}
