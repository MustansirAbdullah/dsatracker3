package com.mustansir.dsatracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import java.time.LocalDate

// GitHub-style grid: columns are weeks (Mon-Sun top to bottom), last column is the current week.
class HeatmapView(ctx: Context, private val onDay: (String) -> Unit) : View(ctx) {

    private val cols = 17
    private val density = resources.displayMetrics.density
    private val gap = 4f * density
    private val radius = 4f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFFE6EDF3.toInt()
    }
    private val levels = intArrayOf(
        0xFF222833.toInt(), 0xFF0E4429.toInt(), 0xFF006D32.toInt(),
        0xFF26A641.toInt(), 0xFF39D353.toInt()
    )
    private var cell = 0f
    private val today: LocalDate = LocalDate.now()
    private val start: LocalDate =
        today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks((cols - 1).toLong())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cell = (width - gap * (cols - 1)) / cols
        setMeasuredDimension(width, (cell * 7 + gap * 6).toInt() + 2)
    }

    private fun level(n: Int) = when {
        n <= 0 -> 0
        n == 1 -> 1
        n == 2 -> 2
        n <= 4 -> 3
        else -> 4
    }

    override fun onDraw(canvas: Canvas) {
        for (ci in 0 until cols) {
            for (r in 0 until 7) {
                val d = start.plusDays((ci * 7 + r).toLong())
                if (d.isAfter(today)) continue
                val key = d.toString()
                val x = ci * (cell + gap)
                val y = r * (cell + gap)
                paint.color = levels[level(Store.dayCount(context, key))]
                canvas.drawRoundRect(x, y, x + cell, y + cell, radius, radius, paint)
                if (Store.photoFile(context, key).exists()) {
                    paint.color = 0xFFFFFFFF.toInt()
                    canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.14f, paint)
                }
                if (d == today) canvas.drawRoundRect(x, y, x + cell, y + cell, radius, radius, outline)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP) {
            val ci = (e.x / (cell + gap)).toInt()
            val r = (e.y / (cell + gap)).toInt()
            if (ci in 0 until cols && r in 0 until 7) {
                val d = start.plusDays((ci * 7 + r).toLong())
                if (!d.isAfter(today)) onDay(d.toString())
            }
            return true
        }
        return super.onTouchEvent(e)
    }
}
