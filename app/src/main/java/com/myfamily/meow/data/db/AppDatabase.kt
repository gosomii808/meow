package com.myfamily.meow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myfamily.meow.data.entity.CorrectionHistory
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.RawPaymentEvent

@Database(
    entities = [RawPaymentEvent::class, ExpenseTransaction::class, CorrectionHistory::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun transactionDao(): TransactionDao
    abstract fun correctionDao(): CorrectionDao

    companion object {
        /** P1: duplicate/transfer flags, AI attempt flag, correction history. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferLikely INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN aiTried INTEGER NOT NULL DEFAULT 0")
                db.execSQL(CREATE_CORRECTION_HISTORY)
                db.execSQL(CREATE_CORRECTION_HISTORY_INDEX)
            }
        }

        // Copied from Room's generated AppDatabase_Impl so the migrated schema validates.
        private const val CREATE_CORRECTION_HISTORY =
            "CREATE TABLE IF NOT EXISTS `correction_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`merchant` TEXT NOT NULL, `merchantKey` TEXT NOT NULL, `foregroundApp` TEXT, " +
                "`predictedCategory` TEXT NOT NULL, `correctedCategory` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        private const val CREATE_CORRECTION_HISTORY_INDEX =
            "CREATE INDEX IF NOT EXISTS `index_correction_history_merchantKey` ON `correction_history` (`merchantKey`)"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "meow.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
