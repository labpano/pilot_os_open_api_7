package com.example.camerasample.live

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.example.camerasample.BaseActivity
import com.example.camerasample.R
import com.example.camerasample.util.*
import com.pi.pano.*
import com.pi.pano.annotation.*
import com.pi.pano.error.PiError
import com.pi.pano.wrap.ProParams
import com.pi.pano.wrap.annotation.PiFps
import com.pi.pano.wrap.annotation.PiPreviewType
import com.pi.pilot.pushsdk.PushParams
import java.util.*
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class LiveActivity : BaseActivity() {

    var startLiving = false
    var liveUrl = ""

    /**
     * 是否是全景直播
     */
    var isPanoramaLive = true

    private lateinit var tvTime: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvUrl: TextView
    private lateinit var btnStartLive: Button
    private lateinit var btnPauseLive: Button
    var liveDuration = 0
    lateinit var mSharedPrefs: SharedPreferences
    var liveTimer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_live)
        tvTime = findViewById(R.id.tv_time)
        tvSpeed = findViewById(R.id.tv_speed)
        tvUrl = findViewById(R.id.tv_url)
        btnStartLive = findViewById(R.id.btn_live)
        btnPauseLive = findViewById(R.id.btn_pause)
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        thread {
            Log.i(TAG, "===init load push [${PilotLib.livePush()}]===")
        }
    }

    override fun onResume() {
        super.onResume()
        initPreview()
    }

    private fun initPreview() {
        liveUrl = mSharedPrefs.getString("sp_live_url", getString(R.string.sp_live_url_1))!!
        tvUrl.text = liveUrl
        isPanoramaLive = mSharedPrefs.getBoolean("switch_live_pano", true)
        val previewLayout = findViewById<FrameLayout>(R.id.preview)
        PilotLib.preview()
            .initPreviewView(previewLayout, PiPreviewType.VIDEO_PANO, object : PanoSDKListener {
                override fun onPanoCreate() {
                    PilotLib.self().setCommCameraCallback(object : PiCallback {
                        override fun onSuccess() {

                        }

                        override fun onError(error: PiError?) {
                            Log.e(TAG, "onError ===> [$error]")
                            if (!startLiving) {
                                changeResolution()
                            } else {
                                stopLive()
                            }
                        }
                    })
                    changeResolution()
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

    fun gotoLiveSetting(view: View) {
        startActivity(Intent(this, LiveSettingsActivity::class.java))
    }

    private fun changeResolution() {
        val listener = if (isPanoramaLive) {
            object : DefaultPanoramaLiveChangeResolutionListener() {
                override fun onSuccess(behavior: Int) {
                    super.onSuccess(behavior)
                    onChangeResolutionSuccess()
                }

                override fun onError(code: Int, message: String?) {
                    super.onError(code, message)
                    onChangeResolutionError(code, message)
                }
            }
        } else {
            object : DefaultScreenLiveChangeResolutionListener(
                mSharedPrefs.getString("sp_live_ratio", getString(R.string.pro_plane_ratio_1))!!
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
        val proParams = ProParams().apply {
            exposeTime = mSharedPrefs.getString("sp_pro_et", "${PiExposureTime.auto}")!!.toInt()
            exposureCompensation =
                mSharedPrefs.getString("sp_pro_ev", "${PiExposureCompensation.normal}")!!.toInt()
            whiteBalance = mSharedPrefs.getString("sp_pro_wb", PiWhiteBalance.auto)
            if (exposeTime != PiExposureTime.auto) {
                iso = mSharedPrefs.getString("sp_pro_iso", "${PiIso.auto}")!!.toInt()
            }
        }
        val params = if (isPanoramaLive) {
            ResolutionParams.Factory.createParamsForPanoramaLive(PiFps._30, proParams)
        } else {
            ResolutionParams.Factory.createParamsForScreenLive(PiFps._30, proParams)
        }
        showDialog("刷新预览")
        PilotLib.preview().changeResolution(params, listener)
    }

    private fun onChangeResolutionSuccess() {
        Log.i(TAG, "changeResolution onSuccess ")
        PilotLib.preview().setSteadyAble(mSharedPrefs.getBoolean("switch_stabilization", true))
        val isFollow = mSharedPrefs.getBoolean("switch_steady_follow", false)
        PilotLib.preview().setSteadyFollow(isFollow, true)

        PilotLib.preview().setAntiMode(mSharedPrefs.getString("sp_pro_anti", PiAntiMode.auto))
        dismissDialog()
    }

    private fun onChangeResolutionError(code: Int, message: String?) {
        dismissDialog()
        Log.e(TAG, "changeResolution onError [$code],[$message] ")
        ToastUtils.show(this@LiveActivity, "预览失败 ：[$code],[$message]")
    }


    fun doLiveClick(view: View) {
        if (startLiving) {
            stopLive()
        } else {
            startLiving()
        }
    }

    private fun startLiving() {
        if (TextUtils.isEmpty(liveUrl) || !liveUrl.startsWith("rtmp://")) {
            ToastUtils.show(this, "直播地址为空 或 异常 ：$liveUrl")
            return
        }
        val data = mSharedPrefs.getString(
            "sp_live_resolution", getString(R.string.camera_resolution_1080p)
        )!!.replace("K", "P")
            .split("P")[1].replace("(", "").replace(")", "")
        val size = Size.parseSize(data).let {
            if (isPanoramaLive) {
                it
            } else {
                val ratio =
                    mSharedPrefs.getString("sp_live_ratio", getString(R.string.pro_plane_ratio_1))
                val resolution = calculatePlanePushResolution(it, ratio!!)
                Log.i(
                    TAG, "startLiving video size [$it] ,ratio[$ratio] ==> [${resolution}]"
                )
                resolution
            }
        }
        Log.i(TAG, "startLiving video size end $size ")

        val bitrate = mSharedPrefs.getString(
            "sp_live_bitrate", getString(R.string.live_bitrate_8)
        )!!.toInt() * 1024 * 1024
        PilotLib.livePush()?.let {
            showDialog("开始直播")
            it.prepare(
                PushParams(
                    size, 30, bitrate, liveUrl
                ).apply {
                    this.isPanorama = mSharedPrefs.getBoolean("switch_live_pano", true)
                    if (mSharedPrefs.getBoolean("switch_live_record", false) &&
                        videoBitrate <= 15 * 1024 * 1024
                    ) {
                        this.recordPath =
                            getExternalFilesDir(Environment.DIRECTORY_DCIM)!!.absolutePath
                        this.recordBlockDuration = mSharedPrefs.getString(
                            "sp_live_split", getString(R.string.video_split_5)
                        )!!.toInt() * 60
                    }
                },
                object : IPushManager.IPushListener {
                    override fun onPushStart() {
                        Log.i(TAG, "直播推流初始化开始")
                        startLiving = true
                        runOnUiThread {
                            btnStartLive.text = "结束直播"
                        }
                    }

                    override fun onPushPrepare() {
                        Log.i(TAG, "初始化准备完成，开始直播")
                        dismissDialog()
                        ToastUtils.show(this@LiveActivity, "直播开始")
                        liveDuration = 0
                        liveTimer = timer("live_timer", startAt = Date(), period = 1000) {
                            liveDuration++
                            runOnUiThread {
                                tvTime.text = formatTimeToHHMMSS(liveDuration)
                                tvSpeed.text = formatBitrate(PilotLib.livePush()!!.realBitrate)
                            }
                        }
                    }

                    override fun onPushResume() {
                        ToastUtils.show(this@LiveActivity, "直播已恢复")
                    }

                    override fun onPushPause() {
                        ToastUtils.show(this@LiveActivity, "直播已暂停")
                    }

                    override fun onPushStop() {
                        ToastUtils.show(this@LiveActivity, "直播已結束")
                        dismissDialog()
                        startLiving = false
                        runOnUiThread {
                            tvTime.text = ""
                            tvSpeed.text = ""
                            btnStartLive.text = "开始直播"
                        }
                        liveTimer?.cancel()
                        liveTimer = null
                    }

                    override fun onPushError(errorCode: Int, error: String?) {
                        dismissDialog()
                        Log.e(TAG, "直播失败 ：$errorCode,,$error")
                        ToastUtils.show(this@LiveActivity, "直播失败 ：$errorCode,,$error")
                        startLiving = false
                        runOnUiThread {
                            btnStartLive.text = "开始直播"
                        }
                    }

                    override fun onPushPathSuccess(filePath: String?) {
                        Log.i(TAG, "一段直播录像已保存 ：$filePath")
                        ToastUtils.show(this@LiveActivity, "直播录像已保存到 : $filePath")
                    }
                })
            it.startPush()
        }
    }

    private fun stopLive() {
        try {
            PilotLib.livePush()?.stopPush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun doLivePauseResume(view: View) {
        PilotLib.livePush()?.apply {
            if (startLiving) {
                pauseLive()
            } else {
                resumeLive()
            }
            startLiving = !startLiving
        }
    }

    private fun pauseLive() {
        PilotLib.livePush()?.pausePush()
        btnPauseLive.text = "恢复直播"
    }

    private fun resumeLive() {
        PilotLib.livePush()?.resumePush()
        // 如果是断网重连
        //this.restartPush()
        btnPauseLive.text = "暂停直播"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLive()
    }

}