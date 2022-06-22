package comp313.sec001.group1.tripez.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import comp313.sec001.group1.tripez.data.Attendee
import comp313.sec001.group1.tripez.data.TripDay
import java.util.*

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long): Date {
        return value.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date): Long {
        return date.time
    }

    @TypeConverter
    fun fromTripDay(tripDay: List<TripDay>): String {
        val type = object : TypeToken<List<TripDay>>() {}.type
        return Gson().toJson(tripDay, type)
    }

    @TypeConverter
    fun toTripDay(string: String): List<TripDay> {
        val type = object : TypeToken<List<TripDay>>() {}.type
        return Gson().fromJson(string, type)
    }

    @TypeConverter
    fun fromAttendee(attendees: List<Attendee>): String {
        val type = object : TypeToken<List<Attendee>>() {}.type
        return Gson().toJson(attendees, type)
    }

    @TypeConverter
    fun toAttendee(string: String): List<Attendee> {
        val type = object : TypeToken<List<Attendee>>() {}.type
        return Gson().fromJson(string, type)
    }
}