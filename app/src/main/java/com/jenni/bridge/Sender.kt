package com.jenni.bridge

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

object Sender {

    private val busy = AtomicBoolean(false)

    fun kick(context: Context) {
        val ctx = context.applicationContext
        if (!busy.compareAndSet(false, true)) return
        val thread = Thread {
            try {
                drain(ctx)
            } catch (ignored: Throwable) {
            } finally {
                busy.set(false)
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun drain(ctx: Context) {
        val prefs = Prefs(ctx)
        val queue = Queue(ctx)
        var attempt = 0
        while (attempt < 6) {
            val entry = queue.first() ?: return
            val outcome = TelegramApi.send(prefs.token, prefs.chat, entry.text)
            if (outcome.ok) {
                queue.remove(entry.id)
                prefs.last = Stamps.now() + " · sent"
            } else {
                attempt++
                prefs.last = Stamps.now() + " · retry: " + outcome.error
                Thread.sleep(2500L * attempt)
            }
        }
    }
}
