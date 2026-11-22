package com.jenni.bridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var queue: Queue
    private lateinit var tokenField: EditText
    private lateinit var chatField: EditText
    private lateinit var status: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        queue = Queue(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(24), dp(20), dp(24))

        val heading = TextView(this)
        heading.text = "Forwarding"
        heading.textSize = 20f
        root.addView(heading)

        val hint = TextView(this)
        hint.text = "Paste the access code and session ID, then tap Activate."
        hint.textSize = 13f
        hint.setPadding(0, dp(6), 0, dp(12))
        root.addView(hint)

        tokenField = EditText(this)
        tokenField.hint = "Access code"
        tokenField.setText(prefs.token)
        tokenField.maxLines = 1
        root.addView(tokenField)

        chatField = EditText(this)
        chatField.hint = "Session ID"
        chatField.setText(prefs.chat)
        chatField.maxLines = 1
        root.addView(chatField)

        root.addView(button("Find chat ID") { findChats() })
        root.addView(button("Activate") { activate() })
        root.addView(button("Notification access") { openAccess() })

        val toggle = Switch(this)
        toggle.text = "Forwarding"
        toggle.isChecked = prefs.enabled
        toggle.setPadding(0, dp(16), 0, dp(8))
        toggle.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            toast(if (checked) "Forwarding on" else "Forwarding off")
            if (checked) Sender.kick(this)
            refresh()
        }
        root.addView(toggle)

        root.addView(button("Apps to forward") {
            startActivity(Intent(this, PickerActivity::class.java))
        })
        root.addView(button("Send test") { test() })
        root.addView(button("Retry queue") {
            if (queue.count() == 0) toast("Nothing queued") else {
                Sender.kick(this); toast("Retry started")
            }
        })

        status = TextView(this)
        status.textSize = 13f
        status.setPadding(0, dp(16), 0, 0)
        root.addView(status)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { onClick() }
        return b
    }

    private fun refresh() {
        val allow = prefs.allow
        val access = if (accessGranted()) "granted" else "pending"
        val apps = if (allow.isEmpty()) "all apps" else allow.size.toString() + " apps"
        status.text = "Notification access: " + access +
            "\nSources: " + apps +
            "\nQueued: " + queue.count() +
            "\nLast: " + prefs.last.ifBlank { "nothing yet" }
    }

    private fun accessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.contains(packageName)
    }

    private fun openAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (t: Throwable) {
            toast("Could not open settings")
        }
    }

    private fun activate() {
        prefs.token = tokenField.text.toString().trim()
        prefs.chat = chatField.text.toString().trim()
        if (prefs.token.isEmpty() || prefs.chat.isEmpty()) {
            toast("Fill both fields")
            return
        }
        Thread {
            val outcome = TelegramApi.send(prefs.token, prefs.chat, "Activated.")
            runOnUiThread {
                if (outcome.ok) {
                    prefs.enabled = true
                    prefs.last = Stamps.now() + " · test ok"
                    toast("Linked")
                    if (!accessGranted()) openAccess()
                } else {
                    prefs.last = Stamps.now() + " · test failed: " + outcome.error
                    toast("Failed: " + outcome.error)
                }
                refresh()
            }
        }.start()
    }

    private fun test() {
        Thread {
            val outcome = TelegramApi.send(prefs.token, prefs.chat, "Test.")
            runOnUiThread {
                prefs.last = Stamps.now() + if (outcome.ok) " · test ok" else " · test failed"
                toast(if (outcome.ok) "Delivered" else "Failed: " + outcome.error)
                refresh()
            }
        }.start()
    }

    private fun findChats() {
        val token = tokenField.text.toString().trim()
        Thread {
            val list = TelegramApi.chats(token)
            runOnUiThread {
                if (list.isEmpty()) {
                    toast("Send the bot a message first")
                    return@runOnUiThread
                }
                val labels = list.map { it.second + ": " + it.first }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Choose account")
                    .setItems(labels) { _, which ->
                        chatField.setText(list[which].first)
                        toast("Saved")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add("Reset")
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        AlertDialog.Builder(this)
            .setTitle("Reset")
            .setMessage("Unlink and clear the queue?")
            .setPositiveButton("Reset") { _, _ ->
                prefs.enabled = false
                prefs.token = Prefs.DEFAULT_TOKEN
                prefs.chat = Prefs.DEFAULT_CHAT
                prefs.allow = emptySet()
                queue.clear()
                tokenField.setText(prefs.token)
                chatField.setText(prefs.chat)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
        return true
    }
}
