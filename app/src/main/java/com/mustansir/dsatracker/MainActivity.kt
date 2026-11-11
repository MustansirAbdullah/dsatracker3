package com.mustansir.dsatracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val BG = 0xFF0E1116.toInt()
    private val CARD = 0xFF171B22.toInt()
    private val CHIP = 0xFF222833.toInt()
    private val TEXT = 0xFFE6EDF3.toInt()
    private val MUTED = 0xFF8B949E.toInt()
    private val ACCENT = 0xFF3FB950.toInt()
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    private var pendingDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Scheduler.schedule(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------- small UI helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(2), stroke)
        }

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 16)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun pill(t: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(TEXT)
        gravity = Gravity.CENTER
        background = rounded(CHIP, 22)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener { onClick() }
    }

    private fun add(parent: LinearLayout, v: View, top: Int = 0) {
        parent.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) })
    }

    private fun statCard(big: String, small: String): LinearLayout = card().apply {
        addView(label(big, 22f, TEXT, true))
        addView(label(small, 12f, MUTED))
    }

    // ---------- main screen ----------

    private fun render() {
        val c = this
        val items = Store.items(c)
        val weeks = Store.weeks(c)
        val w = Store.week(c)
        val done = Store.doneCount(c)
        val total = items.size

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(32))
        }

        add(root, label("WEEK ${w + 1} OF ${weeks.size}", 12f, ACCENT, true))
        add(root, label(weeks[w], 26f, TEXT, true), 4)

        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(CHIP, 4)
        }
        bar.addView(
            View(c).apply { background = rounded(ACCENT, 4) },
            LinearLayout.LayoutParams(0, dp(8), done.toFloat())
        )
        bar.addView(View(c), LinearLayout.LayoutParams(0, dp(8), (total - done).toFloat()))
        add(root, bar, 14)
        add(root, label("$done/$total done this week", 13f, MUTED), 6)

        // stats
        val stats = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(
            statCard("\uD83D\uDD25 ${Store.streak(c)}", "day streak"),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        )
        stats.addView(
            statCard("${Store.dayCount(c, Store.today())}", "ticked today"),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        add(root, stats, 20)

        // checklist
        val list = card()
        items.forEachIndexed { i, t ->
            val isDone = Store.checked(c, i)
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            val box = TextView(c).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(BG)
                setTypeface(null, Typeface.BOLD)
                text = if (isDone) "✓" else ""
                background = if (isDone) rounded(ACCENT, 12) else rounded(CARD, 12, MUTED)
            }
            row.addView(box, LinearLayout.LayoutParams(dp(24), dp(24)))
            val tv = label(t, 16f, if (isDone) MUTED else TEXT)
            if (isDone) tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            row.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(14) })
            row.setOnClickListener {
                Store.toggle(c, i)
                ChecklistWidget.refresh(c)
                render()
            }
            list.addView(row)
        }
        add(root, list, 16)

        // heatmap
        val hm = card()
        hm.addView(label("Activity", 14f, TEXT, true))
        hm.addView(
            HeatmapView(c) { showDay(it) },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
        )
        hm.addView(
            label("Tap a day to see it or add a photo. Dot = has a photo.", 12f, MUTED),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
        )
        add(root, hm, 16)

        // week nav
        val nav = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(
            pill("◀  Prev week") { Store.setWeek(c, w - 1); ChecklistWidget.refresh(c); render() },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        )
        nav.addView(
            pill("Next week  ▶") { Store.setWeek(c, w + 1); ChecklistWidget.refresh(c); render() },
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        add(root, nav, 16)

        // settings
        add(root, pill("Edit checklist items") {
            editList("Checklist items", Store.items(c), "One item per line. Max ${Store.MAX_ITEMS}.") {
                Store.setItems(c, it)
            }
        }, 8)
        add(root, pill("Edit weekly topics") {
            editList("Weekly topics", Store.weeks(c), "One topic per line, in order.") {
                Store.setWeeks(c, it)
            }
        }, 8)
        val (h, m) = Store.reminder(c)
        add(root, pill("Daily reminder: %02d:%02d".format(h, m)) {
            TimePickerDialog(c, { _, nh, nm ->
                Store.setReminder(c, nh, nm)
                Scheduler.schedule(c)
                render()
            }, h, m, true).show()
        }, 8)

        setContentView(ScrollView(c).apply {
            setBackgroundColor(BG)
            addView(root)
        })
    }

    // ---------- dialogs ----------

    private fun editList(title: String, current: List<String>, hint: String, save: (List<String>) -> Unit) {
        val et = EditText(this).apply {
            setText(current.joinToString("\n"))
            setTextColor(TEXT)
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(hint)
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                save(et.text.toString().split("\n"))
                ChecklistWidget.refresh(this)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDay(date: String) {
        val c = this
        val n = Store.dayCount(c, date)
        val f = Store.photoFile(c, date)
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        box.addView(label(if (n == 0) "Nothing ticked this day." else "$n item(s) ticked.", 15f, TEXT))
        if (f.exists()) {
            val bmp = BitmapFactory.decodeFile(f.path)
            if (bmp != null) {
                box.addView(
                    ImageView(c).apply {
                        setImageBitmap(bmp)
                        adjustViewBounds = true
                        maxHeight = dp(360)
                    },
                    LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
                )
            }
        }
        val b = AlertDialog.Builder(c)
            .setTitle(date)
            .setView(box)
            .setPositiveButton(if (f.exists()) "Replace photo" else "Add photo") { _, _ ->
                pendingDate = date
                pickPhoto()
            }
            .setNegativeButton("Close", null)
        if (f.exists()) {
            b.setNeutralButton("Remove photo") { _, _ ->
                f.delete()
                render()
            }
        }
        b.show()
    }

    // ---------- photos ----------

    private fun pickPhoto() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(i, "Pick a photo"), REQ_PHOTO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val date = pendingDate
        val uri = data?.data
        if (requestCode == REQ_PHOTO && resultCode == RESULT_OK && date != null && uri != null) {
            savePhoto(uri, date)
            render()
        }
    }

    private fun savePhoto(uri: Uri, date: String) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return
            val orientation = contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            val deg = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val out = if (deg != 0f) {
                Bitmap.createBitmap(
                    bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true
                )
            } else bmp
            val f = Store.photoFile(this, date)
            f.parentFile?.mkdirs()
            FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save photo", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val REQ_PHOTO = 7
    }
}
