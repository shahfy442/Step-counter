package com.rshah.steps

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** Shared state + persistence. Activity and service run in the same process. */
object StepRepo {
    private lateinit var sp: SharedPreferences
    private var ready = false

    val steps = MutableStateFlow(0)
    val heightCm = MutableStateFlow(170)
    val goal = MutableStateFlow(8000)
    val sensitivity = MutableStateFlow(0.5f)
    val running = MutableStateFlow(false)

    private fun today() = LocalDate.now().toString()

    @Synchronized
    fun init(ctx: Context) {
        if (!ready) {
            sp = ctx.applicationContext.getSharedPreferences("steps", Context.MODE_PRIVATE)
            heightCm.value = sp.getInt("height", 170)
            goal.value = sp.getInt("goal", 8000)
            sensitivity.value = sp.getFloat("sens", 0.5f)
            ready = true
        }
        if (sp.getString("day", null) == today()) {
            steps.value = sp.getInt("count", 0)
        } else {
            steps.value = 0
            sp.edit().putString("day", today()).putInt("count", 0).apply()
        }
    }

    /** Adds steps; resets automatically when the date changes. */
    @Synchronized
    fun add(n: Int) {
        if (sp.getString("day", null) != today()) {
            steps.value = 0
            sp.edit().putString("day", today()).apply()
        }
        val v = steps.value + n
        steps.value = v
        sp.edit().putInt("count", v).apply()
    }

    fun setHeight(v: Int) { heightCm.value = v; sp.edit().putInt("height", v).apply() }
    fun setGoal(v: Int) { goal.value = v; sp.edit().putInt("goal", v).apply() }
    fun setSensitivity(v: Float) { sensitivity.value = v; sp.edit().putFloat("sens", v).apply() }

    /** Stride ≈ 0.415 × height for walking. */
    fun strideMeters(): Float = heightCm.value * 0.415f / 100f
}
