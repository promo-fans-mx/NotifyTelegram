package com.jenni.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Queue(context: Context) {

    class Entry(val id: Long, val text: String)

    private val sp = context.applicationContext
        .getSharedPreferences("bridge_queue", Context.MODE_PRIVATE)

    @Synchronized
    fun add(text: String) {
        val items = load()
        items.add(Entry(System.nanoTime(), text))
        while (items.size > 200) items.removeAt(0)
        save(items)
    }

    @Synchronized
    fun first(): Entry? = load().firstOrNull()

    @Synchronized
    fun remove(id: Long) = save(load().filter { it.id != id })

    @Synchronized
    fun count(): Int = load().size

    @Synchronized
    fun clear() {
        sp.edit().clear().apply()
    }

    private fun load(): MutableList<Entry> {
        val raw = sp.getString("items", null) ?: return ArrayList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return ArrayList()
        val out = ArrayList<Entry>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out.add(Entry(o.optLong("id"), o.optString("text")))
        }
        return out
    }

    private fun save(items: List<Entry>) {
        val array = JSONArray()
        for (e in items) {
            array.put(JSONObject().put("id", e.id).put("text", e.text))
        }
        sp.edit().putString("items", array.toString()).apply()
    }
}
