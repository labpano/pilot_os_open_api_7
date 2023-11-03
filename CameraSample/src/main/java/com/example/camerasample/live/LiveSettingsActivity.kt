package com.example.camerasample.live

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.example.camerasample.R

class LiveSettingsActivity : AppCompatActivity() {

    lateinit var mSharedPrefs: SharedPreferences
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
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_live_setting, rootKey)
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
            findPreference<ListPreference>("sp_live_ratio")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<EditTextPreference>("sp_live_url")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.text
            }
            findPreference<ListPreference>("sp_live_split")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = "${this.value} 分钟"
            }
            findPreference<SwitchPreference>("switch_live_pano")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                findPreference<ListPreference>("sp_live_resolution")?.let {
                    it.setEntries(if (isChecked) R.array.arrays_live_resolution else R.array.arrays_live_resolution_plane)
                    it.entryValues = it.entries
                }
            }
            findPreference<ListPreference>("sp_live_resolution")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = this.value
            }
            findPreference<ListPreference>("sp_live_bitrate")?.apply {
                onPreferenceChangeListener = this@SettingsFragment
                summary = "${this.value} Mbps"
            }
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val stringValue = newValue.toString()
            Log.i(
                tag, "onPreferenceChange===key[${preference.key}]===[$stringValue]"
            )
            if (preference is ListPreference) {
                val index = preference.findIndexOfValue(stringValue)
                if (index >= 0) {
                    when (preference.key) {
                        "sp_live_split" -> {
                            preference.setSummary("${preference.entries[index]} 分钟")
                        }
                        "sp_live_resolution" -> {
                            preference.setSummary(preference.entries[index])
                            onResolutionChanged(stringValue)
                        }
                        else -> {
                            preference.setSummary(preference.entries[index])
                        }
                    }
                }
            } else if (preference is SwitchPreference) {
                if (preference.key == "switch_live_pano") {
                    onLiveTypeChanged(stringValue)
                }
            } else {
                preference.summary = stringValue
            }
            return true
        }

        private fun onLiveTypeChanged(stringValue: String) {
            val pano = stringValue.toBoolean()
            findPreference<ListPreference>("sp_live_resolution")?.let {
                it.setEntries(if (pano) R.array.arrays_live_resolution else R.array.arrays_live_resolution_plane)
                it.entryValues = it.entries
                resetListValue(it)
                onResolutionChanged(it.value)
            }
        }

        private fun onResolutionChanged(resolution: String) {
            findPreference<ListPreference>("sp_live_bitrate")?.let { bitrate ->
                when (resolution) {
                    getString(R.string.camera_resolution_4k) -> {
                        bitrate.setEntries(R.array.arrays_live_bitrate4k)
                        bitrate.entryValues = bitrate.entries
                        resetListValue(bitrate)
                    }
                    getString(R.string.camera_resolution_960p) -> {
                        bitrate.setEntries(R.array.arrays_live_bitrate960)
                        bitrate.entryValues = bitrate.entries
                        resetListValue(bitrate)
                    }
                    getString(R.string.camera_resolution_720p) -> {
                        bitrate.setEntries(R.array.arrays_live_bitrate720)
                        bitrate.entryValues = bitrate.entries
                        resetListValue(bitrate)
                    }
                    getString(R.string.camera_resolution_640p) -> {
                        bitrate.setEntries(R.array.arrays_live_bitrate640)
                        bitrate.entryValues = bitrate.entries
                        resetListValue(bitrate)
                    }
                    else -> {
                        bitrate.setEntries(R.array.arrays_live_bitrate1280)
                        bitrate.entryValues = bitrate.entries
                        resetListValue(bitrate)
                    }
                }
                bitrate.summary = "${bitrate.summary} Mbps"
            }
        }

        private fun resetListValue(pre: ListPreference) {
            pre.setValueIndex(0)
            pre.summary = pre.entryValues[0]
        }

    }
}