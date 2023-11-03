package com.example.camerasample.stitch

import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.camerasample.R
import com.example.camerasample.util.TAG
import com.example.camerasample.util.ToastUtils
import com.pi.pano.PilotLib
import com.pi.pano.wrap.PhotoStitchWrap
import java.io.File
import kotlin.concurrent.thread

class ImageStitchActivity : AppCompatActivity() {

    lateinit var tvName: TextView
    lateinit var btnStitch: Button
    lateinit var tvResult: TextView
    var stitchFile: File? = null
    var stitching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_stitch)

        val dir = getExternalFilesDir(Environment.DIRECTORY_DCIM) ?: return
        val listChild = dir.listFiles() ?: return
        var childFile: File? = null
        listChild.forEach {
            if (it.isFile && it.name.endsWith(".jpg")) {
                childFile = it
                return@forEach
            }
        }
        if (childFile == null || !childFile!!.exists()) {
            ToastUtils.show(this, "未找到可拼接照片，请先前去拍照")
            return
        }
        tvName = findViewById(R.id.tv_name)
        tvName = findViewById(R.id.tv_name)
        tvResult = findViewById(R.id.tvResult)
        btnStitch = findViewById(R.id.btnStitch)
        stitchFile = childFile!!

        tvName.text = "找到准备拼接文件 [$stitchFile]"
    }

    fun doStitchClick(view: View) {
        if (stitching) {
            return
        } else {
            stitchFile()
        }
    }

    private fun stitchFile() {
        Log.i(TAG, "stitchFile =========> [$stitchFile]")
        stitchFile?.apply {
            if (name.endsWith(".jpg")) {
                tvName.text = absolutePath
                thread {
                    val params = PhotoStitchWrap.Factory.create(absolutePath, null, false)
                    PilotLib.stitch().stitchPhoto(params)?.apply {
                        if (TextUtils.isEmpty(this) || !File(this).exists()) {
                            runOnUiThread {
                                tvResult.text = "拼接失败[$absolutePath]"
                            }
                            return@thread
                        }
                        runOnUiThread {
                            tvResult.text = "拼接成功 [$this]"
                            ToastUtils.show(this@ImageStitchActivity, "拼接成功 [$this]")
                        }
                    }
                }
            }
        }
    }

}