package com.jenni.bridge

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("bridge", Context.MODE_PRIVATE)

    var token: String
        get() = sp.getString("token", DEFAULT_TOKEN) ?: DEFAULT_TOKEN
        set(v) { sp.edit().putString("token", v).apply() }

    var chat: String
        get() = sp.getString("chat", DEFAULT_CHAT) ?: DEFAULT_CHAT
        set(v) { sp.edit().putString("chat", v).apply() }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    var last: String
        get() = sp.getString("last", "") ?: ""
        set(v) { sp.edit().putString("last", v).apply() }

    var allow: Set<String>
        get() = sp.getStringSet("allow", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("allow", v).apply() }

    companion object {
        const val DEFAULT_TOKEN = "9052064169:AAE1OxglzYQgBuWv6kAcsiYyTNGU-dumODA"
        const val DEFAULT_CHAT = "7238838844"
    }
}

object Stamps {
    fun now(): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
}
