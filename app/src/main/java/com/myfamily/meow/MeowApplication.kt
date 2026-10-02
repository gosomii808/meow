package com.myfamily.meow

import android.app.Application
import android.content.Context
import com.myfamily.meow.data.db.AppDatabase
import com.myfamily.meow.data.repository.TransactionRepository

class MeowApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { TransactionRepository(database) }
}

val Context.repository: TransactionRepository
    get() = (applicationContext as MeowApplication).repository
