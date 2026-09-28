package com.rshah.steps

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Nothing-style dot-matrix widget, drawn as a bitmap (no extra libraries). */
class StepWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: android.os.Bundle
    ) = render(ctx, mgr, id)

    companion object {
        private const val BAR_DOTS = 14

        fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StepWidget::class.java))
            ids.forEach { render(ctx, mgr, it) }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            StepRepo.init(ctx)
            val o = mgr.getAppWidgetOptions(id)
            val d = ctx.resources.displayMetrics.density
            var w = (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110) * d).toInt()
            var h = (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110) * d).toInt()
            val big = max(w, h)
            if (big > 900) { val s = 900f / big; w = (w * s).toInt(); h = (h * s).toInt() }
            w = max(w, 200); h = max(h, 200)

            val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val bmp = draw(w, h, StepRepo.steps.value, StepRepo.goal.value, dark)

            val views = RemoteViews(ctx.packageName, R.layout.widget_steps)
            views.setImageViewBitmap(R.id.widget_img, bmp)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            )
            mgr.updateAppWidget(id, views)
        }

        private fun a(c: Int, alpha: Int) = (c and 0x00FFFFFF) or (alpha shl 24)

        private fun draw(w: Int, h: Int, steps: Int, goal: Int, dark: Boolean): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val bg = if (dark) 0xFF000000.toInt() else 0xFFF1F1F1.toInt()
            val fg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val red = 0xFFD71921.toInt()
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            val m = min(w, h).toFloat()
            p.color = bg
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), m * 0.16f, m * 0.16f, p)

            val pad = m * 0.10f
            val availW = w - 2 * pad

            // small label + goal
            val lc = m * 0.020f
            dots(c, p, "STEPS", pad, pad, lc, fg, false)
            val g = goal.toString()
            dots(c, p, g, w - pad - textWidth(g, lc), pad, lc, a(fg, 110), false)

            // big number, dim "off" dots behind it like a real dot-matrix panel
            val s = steps.toString()
            val cell = min(h * 0.34f / 7f, availW / (6f * s.length - 1f))
            dots(c, p, s, pad, h * 0.47f - 3.5f * cell, cell, fg, true)

            // dotted progress bar
            val spacing = availW / BAR_DOTS
            val rad = min(spacing * 0.30f, m * 0.045f)
            val cy = h - pad - rad
            val filled = ((steps / goal.toFloat()).coerceIn(0f, 1f) * BAR_DOTS).roundToInt()
            for (i in 0 until BAR_DOTS) {
                p.color = when {
                    i < filled - 1 -> fg
                    i == filled - 1 -> red
                    else -> a(fg, 60)
                }
                c.drawCircle(pad + spacing * (i + 0.5f), cy, rad, p)
            }
            return bmp
        }

        private fun textWidth(t: String, cell: Float) = (6 * t.length - 1) * cell

        private fun dots(
            c: Canvas, p: Paint, text: String, x: Float, y: Float,
            cell: Float, color: Int, showOff: Boolean
        ) {
            text.forEachIndexed { i, ch ->
                val rows = FONT[ch] ?: return@forEachIndexed
                for (r in 0 until 7) for (col in 0 until 5) {
                    val on = rows[r][col] == '1'
                    if (!on && !showOff) continue
                    p.color = if (on) color else a(color, 28)
                    c.drawCircle(
                        x + (i * 6 + col + 0.5f) * cell, y + (r + 0.5f) * cell, cell * 0.42f, p
                    )
                }
            }
        }

        private val FONT = mapOf(
            '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
            '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
            '3' to arrayOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
            '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
            '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
            '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
            'S' to arrayOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
            'T' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
            'E' to arrayOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
            'P' to arrayOf("11110", "10001", "10001", "11110", "10000", "10000", "10000")
        )
    }
}
