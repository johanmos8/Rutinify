package com.mirkwood.rutinify.data.local

import androidx.room.TypeConverter
import com.mirkwood.rutinify.domain.model.AlarmStatus
import com.mirkwood.rutinify.domain.model.OrderStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Conversores Room (design §5). Las fechas ISO se ordenan bien como texto. */
class Converters {

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.format(TIME_FORMAT)

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it, TIME_FORMAT) }

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun orderStatusToString(value: OrderStatus): String = value.name

    @TypeConverter
    fun stringToOrderStatus(value: String): OrderStatus = OrderStatus.valueOf(value)

    @TypeConverter
    fun alarmStatusToString(value: AlarmStatus): String = value.name

    @TypeConverter
    fun stringToAlarmStatus(value: String): AlarmStatus = AlarmStatus.valueOf(value)

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
