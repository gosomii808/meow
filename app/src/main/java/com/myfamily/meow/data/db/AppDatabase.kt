package com.myfamily.meow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myfamily.meow.data.entity.CorrectionHistory
import com.myfamily.meow.data.entity.ExpenseTransaction
import com.myfamily.meow.data.entity.NotificationLog
import com.myfamily.meow.data.entity.RawPaymentEvent

@Database(
    entities = [RawPaymentEvent::class, ExpenseTransaction::class, CorrectionHistory::class, NotificationLog::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun transactionDao(): TransactionDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun notificationLogDao(): NotificationLogDao

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

        /** Income detection and the debug notification log. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN direction TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL(CREATE_NOTIFICATION_LOG)
            }
        }

        /** Split-bill settlement (up swipe). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN splitCount INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN originalAmount INTEGER")
            }
        }

        // Copied from Room's generated AppDatabase_Impl so the migrated schema validates.
        private const val CREATE_CORRECTION_HISTORY =
            "CREATE TABLE IF NOT EXISTS `correction_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`merchant` TEXT NOT NULL, `merchantKey` TEXT NOT NULL, `foregroundApp` TEXT, " +
                "`predictedCategory` TEXT NOT NULL, `correctedCategory` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        private const val CREATE_CORRECTION_HISTORY_INDEX =
            "CREATE INDEX IF NOT EXISTS `index_correction_history_merchantKey` ON `correction_history` (`merchantKey`)"
        private const val CREATE_NOTIFICATION_LOG =
            "CREATE TABLE IF NOT EXISTS `notification_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`packageName` TEXT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`postedAt` INTEGER NOT NULL, `decision` TEXT NOT NULL)"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "meow.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
