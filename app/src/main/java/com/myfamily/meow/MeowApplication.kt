package com.myfamily.meow

import android.app.Application
import android.content.Context
import com.myfamily.meow.ai.AiCategorizer
import com.myfamily.meow.ai.GemmaEngine
import com.myfamily.meow.data.db.AppDatabase
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.reminder.DailyReviewScheduler
import kotlinx.coroutines.launch

class MeowApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { TransactionRepository(database) }
    val gemmaEngine by lazy { GemmaEngine(this) }
    val aiCategorizer by lazy { AiCategorizer(gemmaEngine, repository) }

    override fun onCreate() {
        super.onCreate()
        DailyReviewScheduler.schedule(this)
        backfillOnce()
    }

    /** One-off migrations that run in Kotlin (not SQL), guarded by a prefs flag. */
    private fun backfillOnce() {
        val prefs = getSharedPreferences("backfill", MODE_PRIVATE)
        if (prefs.getBoolean("correction_keys_v6", false)) return
        appScope.launch {
            runCatching { repository.backfillCorrectionKeys() }
            prefs.edit().putBoolean("correction_keys_v6", true).apply()
        }
    }
}

private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

private val Context.app get() = applicationContext as MeowApplication

val Context.repository: TransactionRepository get() = app.repository

val Context.aiCategorizer: AiCategorizer get() = app.aiCategorizer

val Context.gemmaEngine: GemmaEngine get() = app.gemmaEngine
