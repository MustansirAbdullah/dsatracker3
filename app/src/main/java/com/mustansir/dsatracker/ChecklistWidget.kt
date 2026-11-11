package com.mustansir.dsatracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class ChecklistWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, mgr, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val i = intent.getIntExtra(EXTRA_INDEX, -1)
            if (i in Store.items(context).indices) {
                Store.toggle(context, i)
                refresh(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.mustansir.dsatracker.TOGGLE"
        const val EXTRA_INDEX = "index"
        private val ITEM_IDS = intArrayOf(
            R.id.item0, R.id.item1, R.id.item2, R.id.item3,
            R.id.item4, R.id.item5, R.id.item6, R.id.item7
        )

        fun refresh(c: Context) {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, ChecklistWidget::class.java))
            ids.forEach { update(c, mgr, it) }
        }

        private fun update(c: Context, mgr: AppWidgetManager, id: Int) {
            val v = RemoteViews(c.packageName, R.layout.widget)
            val items = Store.items(c)
            val weeks = Store.weeks(c)
            val w = Store.week(c)
            v.setTextViewText(R.id.title, "Week ${w + 1}: ${weeks[w]}")
            v.setTextViewText(
                R.id.progress,
                "${Store.doneCount(c)}/${items.size} done  ·  \uD83D\uDD25 ${Store.streak(c)}"
            )

            ITEM_IDS.forEachIndexed { i, rid ->
                if (i < items.size) {
                    val done = Store.checked(c, i)
                    v.setViewVisibility(rid, View.VISIBLE)
                    v.setTextViewText(rid, (if (done) "☑ " else "☐ ") + items[i])
                    v.setTextColor(rid, if (done) 0xFF3FB950.toInt() else 0xFFE6EDF3.toInt())
                    val intent = Intent(c, ChecklistWidget::class.java)
                        .setAction(ACTION_TOGGLE)
                        .putExtra(EXTRA_INDEX, i)
                    v.setOnClickPendingIntent(
                        rid,
                        PendingIntent.getBroadcast(
                            c, i, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                } else {
                    v.setViewVisibility(rid, View.GONE)
                }
            }

            val open = PendingIntent.getActivity(
                c, 100, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.title, open)
            mgr.updateAppWidget(id, v)
        }
    }
}
