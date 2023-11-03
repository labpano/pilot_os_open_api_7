package com.example.camerasample

import android.app.Application
import com.pi.pano.PilotLib
import com.pi.pano.wrap.IMetadataCallback

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PilotLib.init(applicationContext)
        PilotLib.self().metadataCallback = object : IMetadataCallback {
            override fun getVersion(): String {
                return "版本信息xxx"
            }

            override fun getArtist(): String {
                return "作者信息xxx"
            }
        }
    }

}