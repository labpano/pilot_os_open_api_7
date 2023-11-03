package com.example.camerasample.player

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.camerasample.R
import com.example.camerasample.util.TAG
import com.example.camerasample.util.ToastUtils
import com.pi.pano.MediaPlayerSurfaceView
import com.pi.pano.MediaPlayerSurfaceView.IVideoPlayControl
import com.pi.pano.PilotLib
import java.io.File

/**
 * 直播播放全景 拼接与未拼接视频播放
 * ps: 如果是平面视频，直接使用android系统自带 mediaPlayer,or其他播放器播放即可
 */
class VideoPlayerActivity : AppCompatActivity() {

    var mVideoPlayControl: IVideoPlayControl? = null
    private lateinit var seekBar: SeekBar
    private lateinit var frameLayout: ViewGroup
    private lateinit var playImageView: ImageView
    private lateinit var replayImageView: ImageView

    var playFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_video_player)
        frameLayout = findViewById<FrameLayout>(R.id.fl_preivew)
        playImageView = findViewById<ImageView>(R.id.playImageView)
        replayImageView = findViewById<ImageView>(R.id.replayImageView)

        val dir = getExternalFilesDir(Environment.DIRECTORY_DCIM) ?: return
        val listChild = dir.listFiles() ?: return
        var childFile: File? = null
        listChild.forEach {
            if (it.isDirectory && it.name.endsWith("_u")) {
                childFile = it
                return@forEach
            } else if (it.isFile && it.name.endsWith("_s.mp4")) {
                childFile = it
                return@forEach
            }
        }
        if (childFile == null) {
            ToastUtils.show(this, "未找到可播放视频，请先前去录像")
            return
        }
        playFile = childFile!!
        Log.i(TAG, "播放文件[$playFile] ")
        frameLayout.setOnClickListener {
            mVideoPlayControl?.apply {
                when (state) {
                    MediaPlayerSurfaceView.VideoPlayState.paused, MediaPlayerSurfaceView.VideoPlayState.prepared -> {
                        play()
                    }
                    MediaPlayerSurfaceView.VideoPlayState.completed -> {
                        seek(0f)
                        play()
                    }
                    MediaPlayerSurfaceView.VideoPlayState.started -> {
                        pause()
                    }
                }
            }
        }
        initSeekbar()
    }

    override fun onResume() {
        super.onResume()
        playFile?.let {
            mVideoPlayControl =
                PilotLib.player().playVideo(frameLayout, it,
                    object : MediaPlayerSurfaceView.VideoPlayListener {
                        override fun onPlayControlInit(playControl: IVideoPlayControl?, ret: Int) {
                            Log.i(TAG, "onPlayControlInit ：[$playControl]")
                        }

                        override fun onPlayControlRelease(playControl: IVideoPlayControl?) {
                            Log.i(TAG, "onPlayControlRelease ：[$playControl]")
                        }

                        override fun onPlayStateChange(
                            playControl: IVideoPlayControl?,
                            state: Int
                        ) {
                            runOnUiThread {
                                playImageView.visibility = View.INVISIBLE
                                replayImageView.visibility = View.INVISIBLE
                                if (state == MediaPlayerSurfaceView.VideoPlayState.completed) {
                                    replayImageView.visibility = View.VISIBLE
                                } else if (state == MediaPlayerSurfaceView.VideoPlayState.paused ||
                                    state == MediaPlayerSurfaceView.VideoPlayState.prepared
                                ) {
                                    playImageView.visibility = View.VISIBLE
                                }
                            }
                        }

                        override fun onPlayTimeChange(
                            playControl: IVideoPlayControl?,
                            currentTime: Float,
                            videoDuration: Float
                        ) {
                            Log.d(
                                TAG,
                                "onPlayTimeChange cur[$currentTime] ,dur[$videoDuration]"
                            )
                            seekBar.progress = (currentTime / videoDuration * 100).toInt()
                        }
                    })
        }
    }

    private fun initSeekbar() {
        seekBar = findViewById(R.id.seekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var lastPlayState = 0
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mVideoPlayControl?.apply {
                        seek(progress.toFloat() / seekBar.max)
                        pause()
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                mVideoPlayControl?.apply {
                    lastPlayState = state
                    pause()
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (lastPlayState == MediaPlayerSurfaceView.VideoPlayState.started) {
                    mVideoPlayControl?.play()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        PilotLib.player().release()
    }

}