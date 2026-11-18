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

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
