package com.example.camerasample

import android.app.ProgressDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    var mDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDialog = ProgressDialog(this)
    }

    open fun showDialog(message: String? = "") {
        runOnUiThread {
            mDialog?.apply {
                setMessage(message)
                show()
            }
        }
    }

    open fun dismissDialog() {
        runOnUiThread {
            mDialog?.dismiss()
        }
    }
}