package com.androidlabs.lab_4

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class PlayerActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var videoView: VideoView? = null
    private lateinit var seekBar: SeekBar
    private lateinit var btnPause: Button
    private lateinit var btnResume: Button
    private lateinit var btnRestart: Button
    private lateinit var btnExit: Button
    private lateinit var coverImage: ImageView
    private lateinit var tvTime: TextView

    private var isUserSeeking = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (!isUserSeeking) {
                if (isVideo) {
                    videoView?.let { vv ->
                        val pos = vv.currentPosition
                        seekBar.progress = pos
                        tvTime.text = formatTime(pos)
                    }
                } else {
                    mediaPlayer?.let { mp ->
                        val pos = mp.currentPosition
                        seekBar.progress = pos
                        tvTime.text = formatTime(pos)
                    }
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private var mediaUri: Uri? = null
    private var isVideo: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_player)

        videoView = findViewById(R.id.videoView)
        seekBar = findViewById(R.id.seekBar)
        btnPause = findViewById(R.id.btnPause)
        btnResume = findViewById(R.id.btnResume)
        btnRestart = findViewById(R.id.btnRestart)
        btnExit = findViewById(R.id.btnExit)
        coverImage = findViewById(R.id.coverImage)
        tvTime = findViewById(R.id.tvTime)

        mediaUri = intent.getParcelableExtra("media_uri")
        isVideo = intent.getBooleanExtra("isVideo", false)

        if (mediaUri == null) {
            showAlert("Error", "Media file not provided!")
            return
        }

        if (isVideo)
            setupVideo(mediaUri!!)
        else
            setupAudio(mediaUri!!)

        btnPause.setOnClickListener { pauseMedia() }
        btnResume.setOnClickListener { resumeMedia() }
        btnRestart.setOnClickListener { restartMedia() }
        btnExit.setOnClickListener { finish() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserSeeking = true
                handler.removeCallbacks(updateSeekBarRunnable)
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
                val pos = sb?.progress ?: 0
                if (isVideo)
                    videoView?.seekTo(pos)
                else
                    mediaPlayer?.seekTo(pos)
                handler.post(updateSeekBarRunnable)
            }
        })
    }

    private fun setupVideo(uri: Uri) {
        videoView?.visibility = android.view.View.VISIBLE
        coverImage.visibility = android.view.View.GONE
        videoView?.apply {
            setVideoURI(uri)
            setOnPreparedListener { mp ->
                seekBar.max = mp.duration
                start()
                updateButtonStates(true)
                handler.post(updateSeekBarRunnable)
            }
            setOnCompletionListener { updateButtonStates(false) }
        }
    }

    private fun setupAudio(uri: Uri) {
        videoView?.visibility = android.view.View.GONE
        coverImage.visibility = android.view.View.VISIBLE
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                coverImage.setImageBitmap(bitmap)
            } else {
                coverImage.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
            }
        } catch (e: Exception) {
            coverImage.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
        } finally {
            retriever.release()
        }
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@PlayerActivity, uri)
            prepareAsync()
            setOnPreparedListener { mp ->
                seekBar.max = mp.duration
                start()
                updateButtonStates(true)
                handler.post(updateSeekBarRunnable)
            }
            setOnCompletionListener { updateButtonStates(false) }
        }
    }

    private fun pauseMedia() {
        if (isVideo)
            videoView?.pause()
        else
            mediaPlayer?.pause()
        updateButtonStates(false)
    }

    private fun resumeMedia() {
        if (isVideo)
            videoView?.start()
        else
            mediaPlayer?.start()
        updateButtonStates(true)
    }

    private fun restartMedia() {
        if (isVideo) {
            videoView?.seekTo(0)
            videoView?.start()
            updateButtonStates(true)
        } else {
            mediaPlayer?.seekTo(0)
            mediaPlayer?.start()
            updateButtonStates(true)
        }
    }

    private fun updateButtonStates(isPlaying: Boolean) {
        if (isPlaying) {
            btnPause.setBackgroundResource(R.drawable.pause)
            btnResume.setBackgroundResource(R.drawable.play_inactive)
        } else {
            btnPause.setBackgroundResource(R.drawable.pause_inactive)
            btnResume.setBackgroundResource(R.drawable.play)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0)
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        else
            String.format("%02d:%02d", minutes, seconds)
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }
}