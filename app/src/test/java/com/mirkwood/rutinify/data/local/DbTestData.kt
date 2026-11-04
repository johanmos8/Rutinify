package com.mirkwood.rutinify.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Datos y BD en memoria compartidos por los tests de Room. */
internal object DbTestData {

    val NOW: Instant = Instant.parse("2026-11-04T10:00:00Z")
    val DATE: LocalDate = LocalDate.of(2026, 11, 4)

    fun inMemoryDb(): RutinifyDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            RutinifyDatabase::class.java,
        ).allowMainThreadQueries().build()

    fun order(
        name: String = "Cachorro",
        date: LocalDate = DATE,
        status: OrderStatus = OrderStatus.ACTIVE,
        createdAt: Instant = NOW,
    ) = RoutineOrderEntity(
        name = name,
        date = date,
        notes = "Notas",
        status = status,
        schemaVersion = 1,
        rawJson = """{"schemaVersion":1}""",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    fun alarm(
        orderId: Long,
        position: Int,
        status: AlarmStatus = AlarmStatus.PENDING,
        time: LocalTime = LocalTime.of(6, 45).plusMinutes(position.toLong()),
        dayOffset: Int = 0,
    ) = AlarmItemEntity(
        orderId = orderId,
        position = position,
        time = time,
        dayOffset = dayOffset,
        title = "Actividad $position",
        description = null,
        durationMin = 10,
        category = "general",
        assignee = null,
        status = status,
        snoozedUntil = null,
        firedAt = null,
        doneAt = null,
        statusChangedAt = NOW,
    )
}
