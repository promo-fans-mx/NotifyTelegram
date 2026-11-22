package com.jenni.bridge

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TelegramApi {

    class Outcome(val ok: Boolean, val error: String)

    fun send(token: String, chat: String, text: String): Outcome {
        var conn: HttpURLConnection? = null
        return try {
            val body = "chat_id=" + enc(chat) +
                "&text=" + enc(text.take(3800)) +
                "&disable_web_page_preview=true"
            conn = open(token, "sendMessage")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val raw = read(conn)
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (code in 200..299 && json?.optBoolean("ok") == true) {
                Outcome(true, "")
            } else {
                Outcome(false, json?.optString("description").orEmpty().ifBlank { "HTTP $code" })
            }
        } catch (t: Throwable) {
            Outcome(false, t.message ?: t.javaClass.simpleName)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun chats(token: String): List<Pair<String, String>> {
        var conn: HttpURLConnection? = null
        return try {
            conn = open(token, "getUpdates")
            val json = JSONObject(read(conn))
            if (!json.optBoolean("ok")) return emptyList()
            val result = json.optJSONArray("result") ?: return emptyList()
            val out = ArrayList<Pair<String, String>>()
            val seen = HashSet<Long>()
            for (i in 0 until result.length()) {
                val chat = result.optJSONObject(i)
                    ?.optJSONObject("message")
                    ?.optJSONObject("chat") ?: continue
                val id = chat.optLong("id")
                if (id == 0L || !seen.add(id)) continue
                val label = chat.optString("title").ifBlank {
                    chat.optString("first_name").ifBlank { id.toString() }
                }
                out.add(Pair(id.toString(), label))
            }
            out
        } catch (t: Throwable) {
            emptyList()
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun open(token: String, method: String): HttpURLConnection =
        (URL("https://api.telegram.org/bot$token/$method").openConnection()
            as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 20000
            requestMethod = "POST"
        }

    private fun read(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
}
