package com.mirkwood.rutinify.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Hand-written migrations, never destructive: plans in progress must survive an update (D-83). */
object Migrations {

    /** v1.2: critical categories (R-15.1, D-66). Existing plans are untouched; no category starts critical. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `category_setting` (`name` TEXT NOT NULL, `critical` INTEGER NOT NULL, PRIMARY KEY(`name`))",
            )
        }
    }

    /** v1.3: local diagnostic counters (R-17, D-72). Nothing else changes. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `diagnostic_counter` (`key` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            )
        }
    }

    /**
     * v1.4: alarm history (R-20.1, D-77). Past "Hecho" come from `done_at` and skipped alarms from their status change;
     * snoozes before v1.4 were never stored, so they count 0.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarm_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alarm_id` INTEGER NOT NULL, " +
                    "`order_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`order_id`) REFERENCES `routine_order`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alarm_event_order_id` ON `alarm_event` (`order_id`)")
            db.execSQL(
                "INSERT INTO alarm_event (alarm_id, order_id, type, at) " +
                    "SELECT id, order_id, 'DONE', done_at FROM alarm_item WHERE done_at IS NOT NULL",
            )
            db.execSQL(
                "INSERT INTO alarm_event (alarm_id, order_id, type, at) " +
                    "SELECT id, order_id, 'SKIPPED', status_changed_at FROM alarm_item WHERE status = 'SKIPPED'",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
