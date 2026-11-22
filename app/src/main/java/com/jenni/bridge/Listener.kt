package com.jenni.bridge

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class Listener : NotificationListenerService() {

    private val seen = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (ignored: Throwable) {
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (!prefs.enabled) return

        val allow = prefs.allow
        if (allow.isNotEmpty() && !allow.contains(pkg)) return
        if (sbn.isOngoing) return

        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = body(extras)
        if (title.isEmpty() && body.isEmpty()) return

        val now = System.currentTimeMillis()
        val key = sbn.key + "#" + (title + "|" + body).hashCode()
        synchronized(seen) {
            val it = seen.entries.iterator()
            while (it.hasNext()) if (now - it.next().value > 30000L) it.remove()
            val previous = seen[key]
            if (previous != null && now - previous < 30000L) return
            seen[key] = now
        }

        val text = "[" + label(pkg) + "] " + (if (title.isEmpty()) "" else title + "\n") + body
        Queue(ctx).add(text.trim())
        Sender.kick(ctx)
        prefs.last = Stamps.now() + " · queued"
    }

    private fun body(extras: Bundle): String {
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.let { if (it.isNotEmpty()) return it }
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
        if (!lines.isNullOrEmpty()) return lines.joinToString("\n")
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    }

    private fun label(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (t: Throwable) {
        pkg
    }
}
