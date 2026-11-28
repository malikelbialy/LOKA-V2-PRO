package com.loka.viva.logic

import android.content.Context
import android.content.SharedPreferences
import com.loka.viva.platform.VWindowPosition
import com.google.gson.Gson

class AppSettings(context: Context) : AutoCloseable {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val editor: SharedPreferences.Editor = prefs.edit()
    private val KEY_IPD = "ipd_value"
    private val KEY_FOV = "fov_value"
    private val KEY_TRANSPARENCY = "transparency_value"
    private val KEY_LANGUAGE = "language_preference"
    private val gson = Gson()

    var ipd: Float = 65f {
        set(value) {
            editor.putFloat(KEY_IPD, value.toFloat())
            editor.apply()
        }
    }

    var fov: Float = 90f {
        set(value) {
            editor.putFloat(KEY_FOV, value.toFloat())
            editor.apply()
        }
    }

    var transparency: Float = 70f {
        set(value) {
            editor.putFloat(KEY_TRANSPARENCY, value.toFloat())
            editor.apply()
        }
    }

    var language: String = "ar" {
        set(value) {
            editor.putString(KEY_LANGUAGE, value)
            editor.apply()
        }
    }

    fun load() {
        ipd = prefs.getFloat(KEY_IPD, 65f).toFloat()
        fov = prefs.getFloat(KEY_FOV, 90f).toFloat()
        transparency = prefs.getFloat(KEY_TRANSPARENCY, 70f).toFloat()
        language = prefs.getString(KEY_LANGUAGE, "ar") ?: "ar"
    }

    override fun close() {}
}