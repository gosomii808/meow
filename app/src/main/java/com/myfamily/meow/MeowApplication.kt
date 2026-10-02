package com.myfamily.meow

import android.app.Application
import android.content.Context
import com.myfamily.meow.ai.AiCategorizer
import com.myfamily.meow.ai.GemmaEngine
import com.myfamily.meow.data.db.AppDatabase
import com.myfamily.meow.data.repository.TransactionRepository
import com.myfamily.meow.reminder.DailyReviewScheduler

class MeowApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { TransactionRepository(database) }
    val gemmaEngine by lazy { GemmaEngine(this) }
    val aiCategorizer by lazy { AiCategorizer(gemmaEngine, repository) }

    override fun onCreate() {
        super.onCreate()
        DailyReviewScheduler.schedule(this)
    }
}

private val Context.app get() = applicationContext as MeowApplication

val Context.repository: TransactionRepository get() = app.repository

val Context.aiCategorizer: AiCategorizer get() = app.aiCategorizer

val Context.gemmaEngine: GemmaEngine get() = app.gemmaEngine
