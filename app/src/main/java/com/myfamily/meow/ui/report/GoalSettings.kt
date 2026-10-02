package com.myfamily.meow.ui.report

import android.content.Context
import com.myfamily.meow.classification.Category

/** Monthly spending goal and optional per-category goals. 0 means "not set". */
class GoalSettings(context: Context) {
    private val prefs = context.getSharedPreferences("goals", Context.MODE_PRIVATE)

    var monthly: Long
        get() = prefs.getLong(KEY_MONTHLY, 0)
        set(value) = prefs.edit().putLong(KEY_MONTHLY, value).apply()

    fun categoryGoals(): Map<Category, Long> =
        Category.entries.associateWith { prefs.getLong(key(it), 0) }.filterValues { it > 0 }

    fun setCategoryGoals(goals: Map<Category, Long>) {
        val edit = prefs.edit()
        Category.entries.forEach { c ->
            val v = goals[c] ?: 0
            if (v > 0) edit.putLong(key(c), v) else edit.remove(key(c))
        }
        edit.apply()
    }

    private fun key(c: Category) = "category_${c.name}"

    private companion object {
        const val KEY_MONTHLY = "monthly"
    }
}
