package com.loka.viva.logic

import android.content.Context
import android.content.SharedPreferences
import com.loka.viva.platform.VWindow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class WindowStore(context: Context) : AutoCloseable {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val KEY_WINDOWS = "windows_viva"
    private val gson = Gson()

    val windows: List<VWindow> get() {
        val json = prefs.getString(KEY_WINDOWS, "[]")
        val type = object : TypeToken<List<VWindow>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun addWindow(w: VWindow) {
        val list = windows + w
        save(list)
    }

    fun removeWindow(id: String) {
        val list = windows.filter { it.id != id }
        save(list)
    }

    fun updateWindow(w: VWindow) {
        val list = windows.map { if (it.id == w.id) w else it }
        save(list)
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(list: List<VWindow>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_WINDOWS, json).apply()
    }

    override fun close() {}
}