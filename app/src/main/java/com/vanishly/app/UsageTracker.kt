package com.vanishly.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks daily erase usage entirely in SharedPreferences — no server, no account needed.
 * Resets automatically to 0 each new calendar day.
 * Uses SimpleDateFormat (API 1+) instead of java.time.LocalDate (API 26+).
 */
object UsageTracker {

    private const val PREFS       = "usage_prefs"
    private const val KEY_COUNT   = "erases_today"
    private const val KEY_DATE    = "last_reset_date"

    /** Maximum free erases per day. */
    const val DAILY_LIMIT = 5

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── Public API ─────────────────────────────────────────────────────────

    /** How many erases the user has performed today. */
    fun erasesToday(ctx: Context): Int {
        resetIfNewDay(ctx)
        return prefs(ctx).getInt(KEY_COUNT, 0)
    }

    /** Whether the user can still erase today (below the daily limit). */
    fun canErase(ctx: Context): Boolean = erasesToday(ctx) < DAILY_LIMIT

    /**
     * Call this every time a successful erase is completed.
     * Returns the new daily count.
     */
    fun recordErase(ctx: Context): Int {
        resetIfNewDay(ctx)
        val p     = prefs(ctx)
        val count = p.getInt(KEY_COUNT, 0) + 1
        p.edit().putInt(KEY_COUNT, count).apply()
        return count
    }

    /** Remaining erases for today. */
    fun remaining(ctx: Context): Int = (DAILY_LIMIT - erasesToday(ctx)).coerceAtLeast(0)

    // ── Private helpers ────────────────────────────────────────────────────

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): String = dateFormat.format(Date())

    private fun resetIfNewDay(ctx: Context) {
        val p = prefs(ctx)
        if (p.getString(KEY_DATE, "") != today()) {
            p.edit().putInt(KEY_COUNT, 0).putString(KEY_DATE, today()).apply()
        }
    }
}
