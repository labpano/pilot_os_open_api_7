package com.example.camerasample.camera

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.example.camerasample.BaseActivity
import com.example.camerasample.R
import com.example.camerasample.util.ToastUtils
import com.example.camerasample.util.calculatePlanePushResolution
import com.example.camerasample.util.formatTimeToHHMMSS
import com.pi.pano.*
import com.pi.pano.annotation.*
import com.pi.pano.error.PiError
import com.pi.pano.wrap.IPhotoListener
import com.pi.pano.wrap.ProParams
import com.pi.pano.wrap.annotation.PiFps
import com.pi.pano.wrap.annotation.PiPreviewType
import com.pi.pano.wrap.annotation.PiTimeLapseTimes
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timer

class CameraActivity : BaseActivity() {

    val TAG = "CameraActivity"

    lateinit var preview: ViewGroup
    lateinit var tvMode: TextView
    lateinit var tvTime: TextView
    lateinit var mSharedPrefs: SharedPreferences
    var curMode = ""
    var isRecord = false
    var recordFilePath = ""
    var recordTimer: Timer? = null
    var recordDuration = 0
    var isTimelapseRecord = false
    var timelapseTime = 0
    var resolution = ""

    private val commCallback = object : PiCallback {
        override fun onSuccess() {

        }

        override fun onError(error: PiError) {
            Log.e(TAG, "commCallback onError =====> [$error]")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_camera)
        preview = findViewById(R.id.preview)
        tvMode = findViewById(R.id.tvMode)
        tvTime = findViewById(R.id.tv_time)
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onResume() {
        super.onResume()
        initPano()
    }

    private fun initPano() {
        curMode = mSharedPrefs.getString("single_list_mode", getString(R.string.mode_photo))!!
        resolution = mSharedPrefs.getString(
            "sp_camera_resolution", getString(R.string.camera_resolution_5_7k)
        )!!
        tvMode.text = "$curMode : ${resolution}"

        isTimelapseRecord = curMode == getString(R.string.mode_video_timelapse)
        timelapseTime = mSharedPrefs.getString("sp_timelapse_times", PiTimeLapseTimes._10)!!.toInt()

        val cameraPreviewType = when (curMode) {
            getString(R.string.mode_video_plane) -> {
                PiPreviewType.VIDEO_PLANE
            }
            getString(R.string.mode_photo) -> {
                PiPreviewType.IMAGE
            }
            else -> {
                PiPreviewType.VIDEO_PANO
            }
        }
        PilotLib.preview().initPreviewView(preview, cameraPreviewType,
            object : PanoSDKListener {
                override fun onPanoCreate() {
                    Log.i(TAG, "onPanoCreate")
                    PilotLib.self().setCommCameraCallback(commCallback)
                    refreshResolution()
                }

                override fun onPanoRelease() {
                }

                override fun onChangePreviewMode(mode: Int) {

                }

                override fun onSingleTap() {
                }

                override fun onEncodeFrame(count: Int) {

                }
            })
    }

    private fun refreshResolution() {
        Log.d(TAG, "refreshResolution ==> [$curMode]")
        val isMainCamera = mSharedPrefs.getBoolean("switch_plane_camera", true)

        val proParams = ProParams().apply {
            exposeTime = mSharedPrefs.getString("sp_pro_et", "${PiExposureTime.auto}")!!.toInt()
            exposureCompensation =
                mSharedPrefs.getString("sp_pro_ev", "${PiExposureCompensation.normal}")!!
                    .toInt()
            whiteBalance = mSharedPrefs.getString("sp_pro_wb", PiWhiteBalance.auto)
            if (exposeTime != PiExposureTime.auto) {
                iso = mSharedPrefs.getString("sp_pro_iso", "${PiIso.auto}")!!.toInt()
            }
        }
        val params = when (curMode) {
            getString(R.string.mode_photo) -> {
                ResolutionParams.Factory.createParamsForPhoto(
                    if (resolution == getString(R.string.camera_resolution_12k))
                        PiResolution._12K else PiResolution._5_7K, proParams
                )
            }
            getString(R.string.mode_video_unstitch) -> {
                when (resolution) {
                    getString(R.string.camera_resolution_8k) -> {
                        ResolutionParams.Factory.createParamsForUnStitchVideo(
                            PiResolution._8K, PiFps._10, proParams
                        )
                    }
                    getString(R.string.camera_resolution_4k) -> {
                        ResolutionParams.Factory.createParamsForUnStitchVideo(
                            PiResolution._4K, PiFps._60, proParams
                        )
                    }
                    else -> {
                        ResolutionParams.Factory.createParamsForUnStitchVideo(
                            PiResolution._5_7K, PiFps._30, proParams
                        )
                    }
                }
            }
            getString(R.string.mode_video_vlog) -> {
                ResolutionParams.Factory.createParamsForVlogVideo(proParams)
            }
            getString(R.string.mode_video_plane) -> {
                ResolutionParams.Factory.createParamsForPlaneVideo(
                    isMainCamera, PiResolution._5_7K, PiFps._30, proParams
                )
            }
            getString(R.string.mode_video_timelapse) -> {
                ResolutionParams.Factory.createParamsForTimeLapseVideo(
                    PiResolution._5_7K,
                    proParams
                )
            }
            else -> {
                ResolutionParams.Factory.createParamsForPhoto(PiResolution._5_7K, proParams)
            }
        }
        PilotLib.preview().changeResolution(params, createListener())
        showDialog("切预览")
    }

    private fun createListener(): DefaultChangeResolutionListener {
        Log.d(TAG, "createListener by [$curMode] ")
        return when (curMode) {
            getString(R.string.mode_photo) -> {
                object : DefaultPhotoChangeResolutionListener() {
                    override fun onSuccess(behavior: Int) {
                        super.onSuccess(behavior)
                        onChangeResolutionSuccess()
                    }

                    override fun onError(code: Int, message: String?) {
                        super.onError(code, message)
                        onChangeResolutionError(code, message)
                    }
                }
            }
            getString(R.string.mode_video_unstitch) -> {
                object : DefaultVideoChangeResolutionListener() {
                    override fun onSuccess(behavior: Int) {
                        super.onSuccess(behavior)
                        onChangeResolutionSuccess()
                    }

                    override fun onError(code: Int, message: String?) {
                        super.onError(code, message)
                        onChangeResolutionError(code, message)
                    }
                }
            }
            getString(R.string.mode_video_vlog) -> {
                object : DefaultVlogVideoChangeResolutionListener() {
                    override fun onSuccess(behavior: Int) {
                        super.onSuccess(behavior)
                        onChangeResolutionSuccess()
                    }

                    override fun onError(code: Int, message: String?) {
                        super.onError(code, message)
                        onChangeResolutionError(code, message)
                    }
                }
            }
            getString(R.string.mode_video_plane) -> {
                object : DefaultPlaneVideoChangeResolutionListener(
                    mSharedPrefs.getString(
                        "sp_plane_field", "120"
                    )!!.toInt(), mSharedPrefs.getString("sp_plane_ratio", "1:1")!!
                ) {
                    override fun onSuccess(behavior: Int) {
                        super.onSuccess(behavior)
                        onChangeResolutionSuccess()
                    }

                    override fun onError(code: Int, message: String?) {
                        super.onError(code, message)
                        onChangeResolutionError(code, message)
                    }
                }
            }
            getString(R.string.mode_video_timelapse) -> {
                object : DefaultTimeVideoChangeResolutionListener() {
                    override fun onSuccess(behavior: Int) {
                        super.onSuccess(behavior)
                        onChangeResolutionSuccess()
                    }

                    override fun onError(code: Int, message: String?) {
                        super.onError(code, message)
                        onChangeResolutionError(code, message)
                    }
                }
            }
            else -> {
                object : DefaultPhotoChangeResolutionListener() {
                    override fun onSuccess(behavior: Int) {
                        super.onSuccess(behavior)
                        onChangeResolutionSuccess()
                    }

                    override fun onError(code: Int, message: String?) {
                        super.onError(code, message)
                        onChangeResolutionError(code, message)
                    }
                }
            }
        }
    }

    fun doCapture(view: View) {
        when (curMode) {
            getString(R.string.mode_photo) -> {
                if (resolution == getString(R.string.camera_resolution_12k)) {
                    showDialog("切到12K预览")
                    PilotLib.preview().startPreviewWithTakeLargePhoto(object : PiCallback {
                        override fun onSuccess() {
                            Log.d(TAG, "onSuccess")
                            dismissDialog()
                            takePhoto(true)
                        }

                        override fun onError(error: PiError?) {
                            Log.d(TAG, "error [$error]")
                            dismissDialog()
                            ToastUtils.show(this@CameraActivity, "切预览失败：[$error]")
                        }
                    })
                } else {
                    takePhoto(false)
                }
            }
            else -> {
                startPanoRecord()
            }
        }
    }

    private val videoListener = object : IVideoListener {
        override fun onRecordStart(params: VideoParams) {
            dismissDialog()
            runOnUiThread { tvTime.visibility = View.VISIBLE }
            recordFilePath = params.currentVideoFilename
            Log.d(TAG, "开始录制 ==> [$recordFilePath]")
            recordTimer =
                timer("record_timer", startAt = Date(), period = 1000L) {
                    recordDuration++
                    Log.d(
                        TAG,
                        "record_time ===> [$recordDuration] ,[${System.currentTimeMillis()}]"
                    )
                    val text = if (isTimelapseRecord) {
                        getString(
                            R.string.timelapse_format, formatTimeToHHMMSS(recordDuration),
                            formatTimeToHHMMSS(recordDuration / timelapseTime)
                        )
                    } else {
                        formatTimeToHHMMSS(recordDuration)
                    }
                    runOnUiThread {
                        tvTime.text = text
                    }
                }
        }

        override fun onRecordStop(filepath: String?) {
            dismissDialog()
            Log.i(TAG, "stopRecord onSuccess ==> $filepath")
            ToastUtils.show(this@CameraActivity, "保存到 :$filepath")
        }

        override fun onRecordError(error: PiError, filepath: String?) {
            Log.e(TAG, "onRecordError ==> [$error] ,[$filepath]")
            val message = if (isRecord) "开始录像" else "结束录像"
            ToastUtils.show(this@CameraActivity, "${message}失败 ：[$error]")
            dismissDialog()
        }
    }

    private fun startPanoRecord() {
        Log.i(TAG, "startPanoRecord ==> $isRecord")
        if (isRecord) {
            //停止录像
            tvTime.visibility = View.GONE
            recordTimer?.cancel()
            showDialog("停止录像")
            PilotLib.capture().stopRecord(false, videoListener)
        } else {
            //开始录像
            recordFilePath = ""
            val enCodeType = if (mSharedPrefs.getBoolean("switch_encode", true))
                PiVideoEncode.h_264 else PiVideoEncode.h_265
            val params = when (curMode) {
                getString(R.string.mode_video_unstitch) -> {
                    val data = when (resolution) {
                        getString(R.string.camera_resolution_8k) -> {
                            Pair(PiResolution._8K, 10)
                        }
                        getString(R.string.camera_resolution_4k) -> {
                            Pair(PiResolution._4K, 60)
                        }
                        else -> {
                            Pair(PiResolution._5_7K, 30)
                        }
                    }
                    VideoParams.Factory.create(
                        DefaultRecordNameProxy(this, curMode),
                        data.first, data.second, enCodeType, 2
                    )
                }
                getString(R.string.mode_video_vlog) -> {
                    VideoParams.Factory.createForVlogVideo(
                        DefaultRecordNameProxy(this, curMode), 30,
                        enCodeType, 2
                    )
                }
                getString(R.string.mode_video_plane) -> {
                    val ratio = mSharedPrefs.getString("sp_plane_ratio", "1:1")
                    val data = resolution.replace("K", "P")
                        .split("P")[1].replace("(", "").replace(")", "")
                    val size = Size.parseSize(data).let {
                        val resolution = calculatePlanePushResolution(it, ratio!!)
                        Log.i(TAG, "planeVideo size [$it] ,ratio[$ratio] ==> [${resolution}]")
                        resolution
                    }
                    Log.i(TAG, "planeVideo size end $size ")

                    VideoParams.Factory.createForPlaneVideo(
                        DefaultRecordNameProxy(this, curMode),
                        "${size.width}*${size.height}",
                        ratio,
                        PilotLib.preview().fps, enCodeType, 2
                    )
                }
                getString(R.string.mode_video_timelapse) -> {
                    VideoParams.Factory.createForTimeLapseVideo(
                        DefaultRecordNameProxy(this, curMode),
                        PiResolution._5_7K, enCodeType, 2,
                        "$timelapseTime"
                    )
                }
                else -> {
                    VideoParams.Factory.create(
                        DefaultRecordNameProxy(this, curMode),
                        PiResolution._5_7K, 30, enCodeType, 2
                    )
                }
            }
            recordDuration = 0
            showDialog("开始录像")
            PilotLib.capture().startRecord(params, videoListener)
        }
        isRecord = !isRecord
    }

    fun gotoSetting(view: View) {
        startActivity(Intent(this, CameraSettingsActivity::class.java))
    }

    private fun takePhoto(largePhoto: Boolean) {
        val hdrCount = if (mSharedPrefs.getBoolean("switch_hdr", false)) PiHdrCount._3p
        else PiHdrCount.out
        showDialog("拍照中")
        val params = PhotoParams.Factory.createParams(
            hdrCount,
            DefaultFilenameProxy(this, curMode),
            getExternalFilesDir(
                Environment.DIRECTORY_DCIM
            )!!.absolutePath,
            PiPhotoFileFormat.jpg,
            when (resolution) {
                getString(R.string.camera_resolution_12k) -> PiResolution._12K
                else -> PiResolution._5_7K
            }
        )
        val listener = object : IPhotoListener {
            override fun onTakeStart() {
                Log.d(TAG, "onTakeStart [$params] ==> ")
            }

            @Deprecated("Deprecated")
            override fun onTakeStart(index: Int) {
                Log.d(TAG, "onTakeStart [$index], [$params] ==> ")
            }

            override fun onTakeEnd() {
                Log.d(TAG, "onTakeEnd [$params] ==> ")
            }

            override fun onTakeSuccess(
                change: Boolean,
                simpleFileName: String?,
                stitchFile: File?,
                unStitchFile: File?
            ) {
                Log.d(TAG, "onTakeSuccess ==> [$simpleFileName],[$unStitchFile]")
                ToastUtils.show(this@CameraActivity, "拍照成功 ：${unStitchFile}")
                if (largePhoto) {
                    //切回默认分辨率
                    PilotLib.preview().restorePreview(object : PiCallback {
                        override fun onSuccess() {
                            Log.e(TAG, "restorePreview onSuccess ")
                            dismissDialog()
                        }

                        override fun onError(error: PiError?) {
                            Log.e(TAG, "restorePreview error ===> [$error]")
                            ToastUtils.show(this@CameraActivity, "恢复预览失败 : [$error]")
                            dismissDialog()
                        }
                    })
                } else {
                    dismissDialog()
                }
            }

            override fun onTakeError(change: Boolean, errorCode: String?) {
                Log.d(TAG, "onTakeError [$change] ,error[$errorCode]==> ")
                dismissDialog()
            }
        }
        PilotLib.capture().takePhoto(params, listener)
    }

    private fun onChangeResolutionSuccess() {
        Log.i(TAG, "onChangeResolutionSuccess")
        PilotLib.preview().setSteadyAble(mSharedPrefs.getBoolean("switch_stabilization", true))
        //防抖方向
        val isFollow = mSharedPrefs.getBoolean("switch_steady_follow", false)
        if (curMode == getString(R.string.mode_video_plane)) {
            PilotLib.preview().setSteadyFollow(isFollow, false)
        } else {
            PilotLib.preview().setSteadyFollow(isFollow, true)
        }
        PilotLib.preview().setAntiMode(mSharedPrefs.getString("sp_pro_anti", PiAntiMode.auto))
        PilotLib.preview().setInPhotoHdr(mSharedPrefs.getBoolean("switch_hdr", false))
        dismissDialog()
    }

    private fun onChangeResolutionError(code: Int, message: String?) {
        Log.w(TAG, "onChangeResolutionError [$code], [$message]")
        dismissDialog()
        ToastUtils.show(this, "切分辨率失败 ：[$code] , [$message]")
    }

    open class DefaultFilenameProxy(var activity: Activity, var mode: String) : IFilenameProxy {
        override fun getBasicName(): String {
            val type = when (mode) {
                activity.getString(R.string.mode_video_vlog) -> {
                    "_vlv"
                }
                activity.getString(R.string.mode_video_plane) -> {
                    "_plv"
                }
                activity.getString(R.string.mode_photo) -> {
                    "_np"
                }
                activity.getString(R.string.mode_video_timelapse) -> {
                    "_tlv"
                }
                else -> {
                    "_nv"
                }
            }
            return generatorBasicName() + type
        }

        companion object {
            fun generatorBasicName(): String {
                val format = SimpleDateFormat("yyMMdd_HHmmssSSS", Locale.getDefault())
                return format.format(Date())
            }
        }
    }

    class DefaultRecordNameProxy(activity: Activity, mode: String) :
        DefaultFilenameProxy(activity, mode),
        IRecordFilenameProxy {

        override fun getParentPath(): File {
            return activity.getExternalFilesDir(
                Environment.DIRECTORY_DCIM
            )!!
        }
    }


}