package com.mustansir.dsatracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Curriculum {
    // Defaults only. You can edit both lists inside the app.
    val defaultWeeks = listOf(
        "Arrays + hashing",
        "Two pointers + sliding window",
        "Stack + queue",
        "Binary search + sorting",
        "Linked lists",
        "Recursion + backtracking",
        "Trees I (traversals, BST)",
        "Trees II (harder problems)",
        "Heaps / priority queues",
        "Graphs I (BFS, DFS)",
        "Graphs II (topo sort, union-find, shortest paths)",
        "Greedy + intervals",
        "DP I (1D)",
        "DP II (2D)",
        "DP III (harder patterns)",
        "Tries, bit manipulation, math"
    )
    val defaultItems = listOf(
        "Watch concept video(s)",
        "Solve problems",
        "Write one-line note"
    )
}

object Store {
    const val MAX_ITEMS = 8

    private fun p(c: Context) = c.getSharedPreferences("dsa", Context.MODE_PRIVATE)

    fun today(): String = LocalDate.now().toString()

    private fun readList(c: Context, key: String, default: List<String>): List<String> {
        val s = p(c).getString(key, null) ?: return default
        val l = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return if (l.isEmpty()) default else l
    }

    private fun writeList(c: Context, key: String, list: List<String>) {
        val l = list.map { it.trim() }.filter { it.isNotEmpty() }
        if (l.isNotEmpty()) p(c).edit().putString(key, l.joinToString("\n")).apply()
    }

    fun items(c: Context): List<String> = readList(c, "items", Curriculum.defaultItems)
    fun setItems(c: Context, list: List<String>) = writeList(c, "items", list.take(MAX_ITEMS))

    fun weeks(c: Context): List<String> = readList(c, "topics", Curriculum.defaultWeeks)
    fun setWeeks(c: Context, list: List<String>) = writeList(c, "topics", list)

    fun week(c: Context): Int = p(c).getInt("week", 0).coerceIn(0, weeks(c).lastIndex)

    fun setWeek(c: Context, w: Int) {
        p(c).edit().putInt("week", w.coerceIn(0, weeks(c).lastIndex)).apply()
    }

    fun checked(c: Context, i: Int): Boolean = p(c).getBoolean("w${week(c)}_$i", false)

    fun toggle(c: Context, i: Int) {
        val k = "w${week(c)}_$i"
        val now = !p(c).getBoolean(k, false)
        val dk = "d_${today()}"
        val cnt = p(c).getInt(dk, 0)
        p(c).edit()
            .putBoolean(k, now)
            .putInt(dk, if (now) cnt + 1 else maxOf(0, cnt - 1))
            .apply()
    }

    fun doneCount(c: Context): Int = items(c).indices.count { checked(c, it) }

    // Number of items ticked on a given day (yyyy-MM-dd). Drives the heatmap and streak.
    fun dayCount(c: Context, date: String): Int = p(c).getInt("d_$date", 0)

    fun streak(c: Context): Int {
        var d = LocalDate.now()
        if (dayCount(c, d.toString()) == 0) d = d.minusDays(1)
        var n = 0
        while (dayCount(c, d.toString()) > 0) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun photoFile(c: Context, date: String): File = File(File(c.filesDir, "photos"), "$date.jpg")

    fun reminder(c: Context): Pair<Int, Int> = p(c).getInt("rh", 21) to p(c).getInt("rm", 0)

    fun setReminder(c: Context, h: Int, m: Int) {
        p(c).edit().putInt("rh", h).putInt("rm", m).apply()
    }
}

object Scheduler {
    fun schedule(c: Context) {
        val (h, m) = Store.reminder(c)
        val now = Calendar.getInstance()
        val next = Calendar.getInstance()
        next.set(Calendar.HOUR_OF_DAY, h)
        next.set(Calendar.MINUTE, m)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        if (next.before(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        val delay = next.timeInMillis - now.timeInMillis
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(c)
            .enqueueUniquePeriodicWork("daily_reminder", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val c = applicationContext
        // Only nag if nothing has been ticked today.
        if (Store.dayCount(c, Store.today()) == 0) {
            val streak = Store.streak(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("daily", "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val pi = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (streak > 0) "Keep your $streak-day streak alive. Do today's bit."
            else "Nothing ticked today. Do today's bit."
            val n = Notification.Builder(c, "daily")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("DSA: ${Store.weeks(c)[Store.week(c)]}")
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
        }
        return Result.success()
    }
}
