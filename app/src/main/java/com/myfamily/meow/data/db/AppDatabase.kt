package com.myfamily.meow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.RawPaymentEvent

@Database(
    entities = [RawPaymentEvent::class, ExpenseTransaction::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "meow.db").build()
    }
}
