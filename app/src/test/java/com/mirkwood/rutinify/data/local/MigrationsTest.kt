package com.mirkwood.rutinify.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** T-43: database migrations keep every plan in progress (D-66, D-83). */
@RunWith(AndroidJUnit4::class)
class MigrationsTest {

    // Absolute path: Room's test driver compares the name with the opened file path, which on Windows uses backslashes
    private val dbName: String = ApplicationProvider.getApplicationContext<Context>()
        .getDatabasePath("migration-test.db").absolutePath

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RutinifyDatabase::class.java)

    /** A v1 database with one active plan and one pending alarm, written as v1 SQL. */
    private fun createV1() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO routine_order (id, name, date, notes, status, schema_version, raw_json, created_at, updated_at) " +
                    "VALUES (1, 'Antibiótico', '2026-11-16', NULL, 'ACTIVE', 1, NULL, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO alarm_item (id, order_id, position, time, day_offset, title, description, duration_min, category, " +
                    "assignee, status, snoozed_until, fired_at, done_at, status_changed_at) " +
                    "VALUES (10, 1, 0, '06:00', 3, 'Amoxicilina', NULL, NULL, 'medicación', NULL, 'PENDING', NULL, NULL, NULL, 0)",
            )
        }
    }

    @Test
    fun `de 1 a 2 crea la tabla de categorias y valida el esquema`() { // D-66, D-83
        createV1()

        helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2).use { db ->
            db.query("SELECT COUNT(*) FROM category_setting").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    @Test
    fun `tras migrar los planes y alarmas siguen igual y ninguna categoria es critica`() = runTest { // R-15.1, D-83
        createV1()
        helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), RutinifyDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val row = requireNotNull(db.orderDao().observeWithAlarms(1).first())
            assertThat(row.order.name).isEqualTo("Antibiótico")
            assertThat(row.order.status).isEqualTo(OrderStatus.ACTIVE)
            val alarm = row.alarms.single()
            assertThat(alarm.time).isEqualTo(LocalTime.of(6, 0))
            assertThat(alarm.dayOffset).isEqualTo(3)
            assertThat(alarm.status).isEqualTo(AlarmStatus.PENDING)
            assertThat(db.categorySettingDao().observeUsed().first())
                .containsExactly(CategorySettingEntity("medicación", critical = false))
        } finally {
            db.close()
        }
    }

    @Test
    fun `de 2 a 3 crea la tabla de diagnostico vacia`() { // R-17.2, D-72, D-83
        helper.createDatabase(dbName, 2).close()

        helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM diagnostic_counter").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    @Test
    fun `de 1 a la ultima version los planes siguen igual`() = runTest { // D-83
        createV1()
        helper.runMigrationsAndValidate(dbName, 4, true, *Migrations.ALL).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), RutinifyDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val row = requireNotNull(db.orderDao().observeWithAlarms(1).first())
            assertThat(row.order.name).isEqualTo("Antibiótico")
            assertThat(row.alarms.single().status).isEqualTo(AlarmStatus.PENDING)
            assertThat(db.diagnosticCounterDao().getAll()).isEmpty()
        } finally {
            db.close()
        }
    }

    @Test
    fun `de 3 a 4 crea el historial con las hechas y las omitidas`() { // R-20.1, D-77, D-83
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL(
                "INSERT INTO routine_order (id, name, date, notes, status, schema_version, raw_json, created_at, updated_at) " +
                    "VALUES (1, 'Cachorro', '2026-11-16', NULL, 'ACTIVE', 1, NULL, 0, 0)",
            )
            listOf(
                "(10, 1, 0, '06:00', 0, 'Pis', NULL, NULL, 'paseo', NULL, 'DONE', NULL, 100, 200, 200)",
                "(11, 1, 1, '07:00', 0, 'Comida', NULL, NULL, 'comida', NULL, 'SKIPPED', NULL, NULL, NULL, 300)",
                "(12, 1, 2, '08:00', 0, 'Siesta', NULL, NULL, 'siesta', NULL, 'PENDING', NULL, NULL, NULL, 0)",
            ).forEach { row ->
                db.execSQL(
                    "INSERT INTO alarm_item (id, order_id, position, time, day_offset, title, description, duration_min, category, " +
                        "assignee, status, snoozed_until, fired_at, done_at, status_changed_at) VALUES $row",
                )
            }
        }

        helper.runMigrationsAndValidate(dbName, 4, true, Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT alarm_id, type, at FROM alarm_event ORDER BY alarm_id").use { cursor ->
                val rows = buildList {
                    while (cursor.moveToNext()) add(Triple(cursor.getLong(0), cursor.getString(1), cursor.getLong(2)))
                }
                assertThat(rows).containsExactly(Triple(10L, "DONE", 200L), Triple(11L, "SKIPPED", 300L)).inOrder()
            }
        }
    }
}
