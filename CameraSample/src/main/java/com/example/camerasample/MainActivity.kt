package com.example.camerasample

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.camerasample.camera.CameraActivity
import com.example.camerasample.live.LiveActivity
import com.example.camerasample.player.ImagePlayerActivity
import com.example.camerasample.player.VideoPlayerActivity
import com.example.camerasample.stitch.ImageStitchActivity
import com.example.camerasample.stitch.VideoStitchActivity
import com.example.camerasample.util.ToastUtils
import com.example.camerasample.util.deleteDir
import com.pi.pano.PiPanoUtils
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val VIDEO_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private val REQUEST_VIDEO_PERMISSIONS = 1

    lateinit var preference: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hasPermissionsGranted()
        preference = PreferenceManager.getDefaultSharedPreferences(this)
        PiPanoUtils.setDev(filesDir.absolutePath, true)
    }

    fun gotoCamera(view: View) {
        startActivity(Intent(this, CameraActivity::class.java))
    }

    fun gotoLiving(view: View) {
        startActivity(Intent(this, LiveActivity::class.java))
    }

    fun gotoVideoPlayer(view: View) {
        startActivity(Intent(this, VideoPlayerActivity::class.java))
    }

    fun gotoImagePlayer(view: View) {
        startActivity(Intent(this, ImagePlayerActivity::class.java))
    }

    fun gotoImageStitch(view: View) {
        startActivity(Intent(this, ImageStitchActivity::class.java))
    }

    fun gotoVideoStitch(view: View) {
        startActivity(Intent(this, VideoStitchActivity::class.java))
    }

    fun clearFile(view: View) {
        thread {
            getExternalFilesDir(Environment.DIRECTORY_DCIM)?.deleteDir()
            ToastUtils.show(this, "清除成功")
        }
    }

    private fun hasPermissionsGranted() {
        for (permission in VIDEO_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS
                )
            }
        }
    }

}