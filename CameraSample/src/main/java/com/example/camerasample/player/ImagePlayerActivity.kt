package com.example.camerasample.player

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.camerasample.R
import com.example.camerasample.util.TAG
import com.example.camerasample.util.ToastUtils
import com.pi.pano.MediaPlayerSurfaceView
import com.pi.pano.PilotLib
import java.io.File

class ImagePlayerActivity : AppCompatActivity() {

    private var iPlayControl: MediaPlayerSurfaceView.IPlayControl? = null
    var playFile: File? = null
    private lateinit var frameLayout: ViewGroup
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_image_player)
        frameLayout = findViewById<FrameLayout>(R.id.fl_preivew)

        val dir = getExternalFilesDir(Environment.DIRECTORY_DCIM) ?: return
        val listChild = dir.listFiles() ?: return
        var childFile: File? = null
        listChild.forEach {
            if (it.isFile && it.name.endsWith(".jpg")) {
                childFile = it
                return@forEach
            }
        }
        if (childFile == null) {
            ToastUtils.show(this, "未找到可播放图片，请先前去拍照")
            return
        }
        Log.i(TAG, "播放文件[$playFile]")
        playFile = childFile
    }

    override fun onResume() {
        super.onResume()
        playFile?.apply {
            iPlayControl = PilotLib.player()
                .playImage(frameLayout, this, object : MediaPlayerSurfaceView.ImagePlayListener {
                    override fun onPlayFailed(ret: Int) {
                        Log.e(TAG, "加载图片失败 ：[$ret]")
                    }

                    override fun onPlayCompleted(file: File?) {
                        Log.i(TAG, "加载图片成功 ：[$file]")
                    }
                })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        iPlayControl?.release()
    }


}