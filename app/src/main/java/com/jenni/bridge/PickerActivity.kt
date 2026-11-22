package com.jenni.bridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class PickerActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var list: ListView
    private lateinit var search: EditText

    private val names = ArrayList<String>()
    private val packages = ArrayList<String>()
    private val chosen = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        chosen.addAll(prefs.allow)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val hint = TextView(this)
        hint.text = "Tap to tick. Nothing ticked = every app."
        hint.setPadding(24, 24, 24, 8)
        root.addView(hint)

        search = EditText(this)
        search.hint = "Search"
        search.maxLines = 1
        root.addView(search)

        list = ListView(this)
        root.addView(list)

        val clear = Button(this)
        clear.text = "Forward everything"
        clear.setOnClickListener {
            chosen.clear()
            prefs.allow = emptySet()
            render(search.text.toString())
        }
        root.addView(clear)

        setContentView(root)

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                render(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        load()
        render("")
    }

    private fun load() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val pairs = ArrayList<Pair<String, String>>()
        for (info in packageManager.queryIntentActivities(intent, 0)) {
            val app = info.activityInfo?.applicationInfo ?: continue
            val pkg = app.packageName
            if (pkg == packageName || !seen.add(pkg)) continue
            pairs.add(Pair(packageManager.getApplicationLabel(app).toString(), pkg))
        }
        pairs.sortBy { it.first.lowercase() }
        for (p in pairs) {
            names.add(p.first)
            packages.add(p.second)
        }
    }

    private fun render(query: String) {
        val shownNames = ArrayList<String>()
        val shownPackages = ArrayList<String>()
        for (i in names.indices) {
            if (query.isNotBlank() && !names[i].contains(query, true)) continue
            shownNames.add(names[i])
            shownPackages.add(packages[i])
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, shownNames)
        list.adapter = adapter
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in shownPackages.indices) {
            list.setItemChecked(i, chosen.contains(shownPackages[i]))
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val pkg = shownPackages[position]
            if (!chosen.add(pkg)) chosen.remove(pkg)
            prefs.allow = chosen
            list.setItemChecked(position, chosen.contains(pkg))
        }
    }
}
