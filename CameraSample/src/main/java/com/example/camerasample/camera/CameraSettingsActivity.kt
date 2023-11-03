package com.example.camerasample.camera

import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.camerasample.R

class CameraSettingsActivity : AppCompatActivity() {
    companion object {
        val TAG = CameraSettingsActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_camera_setting, rootKey)
            findPreference<ListPreference>("single_list_mode")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
                findPreference<ListPreference>("sp_camera_resolution")?.let {
                    it.setEntries(getResolutions(value))
                    it.entryValues = it.entries
                }
            }
            findPreference<ListPreference>("sp_pro_et")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_pro_ev")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_pro_wb")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_pro_iso")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_pro_anti")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_plane_field")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_plane_ratio")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_timelapse_times")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_camera_resolution")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
                Log.i(
                    TAG, "sp_camera_resolution ==> [${value}] ,[${entries.contentToString()}]"
                )
            }
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val stringValue = newValue.toString()
            if (preference is ListPreference) {
                val index = preference.findIndexOfValue(stringValue)
                if (index >= 0) {
                    preference.setSummary(preference.entries[index])
                    if (preference.key == "single_list_mode") {
                        onCameraModeChanged(stringValue)
                    }
                }
            } else {
                preference.summary = stringValue
            }
            return true
        }

        private fun onCameraModeChanged(key: String?) {
            findPreference<ListPreference>("sp_camera_resolution")?.let { resolutions ->
                resolutions.setEntries(getResolutions(key))
                resolutions.entryValues = resolutions.entries
                resetListValue(resolutions)
            }
        }

        private fun resetListValue(pre: ListPreference) {
            pre.setValueIndex(0)
            pre.summary = pre.entryValues[0]
        }

        private fun getResolutions(key: String?): Int {
            return when (key) {
                getString(R.string.mode_photo) -> R.array.arrays_camera_resolution_photo
                getString(R.string.mode_video_unstitch) -> R.array.arrays_camera_resolution_video_un
                getString(R.string.mode_video_plane) -> R.array.arrays_camera_resolution_video_plane
                getString(R.string.mode_video_vlog) -> R.array.arrays_camera_resolution_video_vlog
                getString(R.string.mode_video_timelapse) -> R.array.arrays_camera_resolution_video_time
                else -> R.array.arrays_camera_resolution_photo
            }
        }

    }
}