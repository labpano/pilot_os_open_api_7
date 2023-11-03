package com.example.camerasample.stitch

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.camerasample.R
import com.example.camerasample.util.*
import com.pi.pano.PilotLib
import com.pi.pano.StitchVideoParams
import com.pi.pano.StitchingListener
import com.pi.pano.StitchingUtil
import java.io.File

class VideoStitchActivity : AppCompatActivity() {

    lateinit var tvName: TextView
    lateinit var btnStitch: Button
    lateinit var tvResult: TextView
    lateinit var progressBar: ProgressBar
    var stitchFile: File? = null
    var stitching = false
    var firstVideoStitch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_stitch)

        val dir = getExternalFilesDir(Environment.DIRECTORY_DCIM) ?: return
        val listChild = dir.listFiles() ?: return
        var childFile: File? = null
        listChild.forEach {
            if (it.isDirectory && it.name.endsWith("_u")) {
                childFile = it
                return@forEach
            }
        }
        if (childFile == null || !childFile!!.exists()) {
            ToastUtils.show(this, "未找到可拼接视频，请先去录像")
            return
        }
        tvName = findViewById(R.id.tv_name)
        progressBar = findViewById(R.id.progress)
        tvResult = findViewById(R.id.tvResult)
        btnStitch = findViewById(R.id.btnStitch)
        stitchFile = childFile!!

        tvName.text = "找到准备拼接文件 [$stitchFile]"
    }

    fun doStitchClick(view: View) {
        if (stitching) {
            PilotLib.stitch().pauseVideoStitchTask()
        } else {
            if (firstVideoStitch) {
                stitchFile()
            } else {
                PilotLib.stitch().startVideoStitchTask()
            }
        }
    }

    private fun stitchFile() {
        Log.i(TAG, "stitchFile =========> [$stitchFile]")
        stitchFile?.apply {
            stitchVideo(this)
        }
    }

    private fun stitchVideo(file: File) {
        var doubleStream = false
        val realFile = if (file.isDirectory) {
            val temp = File(file, "0.mp4")
            if (temp.exists()) {
                if (File(file, "1.mp4").exists()) {
                    doubleStream = true
                }
                file
            } else {
                null
            }
        } else {
            null
        }
        if (realFile == null) {
            ToastUtils.show(this, "异常视频(未找到0.mp4文件)，无法拼接 [$file]")
            return
        }
        PilotLib.stitch().setVideoStitchListener(object : StitchingListener {
            override fun onStitchingProgressChange(task: StitchingUtil.Task) {
                runOnUiThread {
                    Log.d(TAG, "拼接中 ===> [${task.progress}]")
                    progressBar.progress = task.progress.toInt()
                }
            }

            override fun onStitchingStateChange(task: StitchingUtil.Task) {
                Log.d(TAG, "onStitchingStateChange ===> [${task.stitchState}]")
                if (task.stitchState == StitchingUtil.StitchState.STITCH_STATE_START) {
                    stitching = true
                } else if (task.stitchState == StitchingUtil.StitchState.STITCH_STATE_STOP ||
                    task.stitchState == StitchingUtil.StitchState.STITCH_STATE_PAUSE
                ) {
                    stitching = false
                }
                runOnUiThread {
                    btnStitch.text = if (stitching) {
                        "暂停拼接"
                    } else {
                        "开始拼接"
                    }
                }
            }

            override fun onStitchingDeleteFinish(task: StitchingUtil.Task) {
                Log.d(TAG, "onStitchingDeleteFinish ===> [${task.stitchState}]")
            }

            override fun onStitchingDeleteDeleting(task: StitchingUtil.Task) {
                Log.d(
                    TAG,
                    "onStitchingDeleteDeleting ===> [${task.stitchState}],[${task.file}],[${task.progress}]"
                )
                if (task.stitchState == StitchingUtil.StitchState.STITCH_STATE_STOP) {
                    Log.i(TAG, "拼接结束")
                    runOnUiThread {
                        val path = task.file.absolutePath.replace("_u", "_s.mp4")
                        tvResult.text = if (File(path).exists()) "拼接成功 [${path}]"
                        else "拼接结束"
                    }
                }
            }
        })
        var size = obtainVideoSize(File(realFile, "0.mp4"))
        if (size == null) {
            ToastUtils.show(this, "视频文件异常[$file]")
            tvResult.text = "文件异常[$file],无法拼接"
            return
        }
        val bitrate = 0
        if (doubleStream) {
            //双码流视频，1个镜头录一个文件，大小*2
            size = Size(size.width * 2, size.height)
        }
        val arrays =
            obtainStitchVideoSize(size.width, size.height, parseVideoFps(realFile))
        val params = StitchVideoParams.Factory.createParams(
            realFile, arrays[0], arrays[1], arrays[2], bitrate
        )
        PilotLib.stitch().addVideoTask(params)
        PilotLib.stitch().startVideoStitchTask()
        firstVideoStitch = false
    }


}