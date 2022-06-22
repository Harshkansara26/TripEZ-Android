package comp313.sec001.group1.tripez.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import comp313.sec001.group1.tripez.CreateTripMutation
import comp313.sec001.group1.tripez.UpdateTripMutation
import comp313.sec001.group1.tripez.UserTripsQuery
import comp313.sec001.group1.tripez.lib.extensions.parseAsDate
import java.text.SimpleDateFormat
import java.util.*

//Trip class
@Entity
data class Trip(
    @PrimaryKey val _id: String,
    val userId: String,
    val tripName: String,
    val tripLocation: String,
    val days: List<TripDay>,
    val atendees: List<Attendee>
)

data class TripDay(
    val date: Date,
    val locations: List<String>,
    val toDos: List<ToDo>,
    val notes: String?
)

data class ToDo(
    val task: String,
    val isCompleted: Boolean
)

data class Attendee(
    val name: String?,
    val email: String?,
    val phone: String?
)

fun UserTripsQuery.Trip.toDomain(): Trip =
    Trip(
        this._id,
        this.userId,
        this.tripName,
        this.tripLocation,
        this.days.map {
            TripDay(
                it.date.parseAsDate(),
                it.location,
                (it.toDo ?: listOf()).map {
                    ToDo(
                        it!!.task,
                        it.isCompleted
                    )
                },
                it.notes
            )
        },
        (this.atendees ?: listOf()).map {
            Attendee(
                it!!.name,
                it.email,
                it.phone
            )
        })

fun UserTripsQuery.Data.toDomain() : List<Trip> =
    this.getUsersTrip.trips.map{it.toDomain()}

fun CreateTripMutation.Data.toDomain(): Trip =
    this.createTrip.let {
        Trip(
            it._id,
            it.userId,
            it.tripName,
            it.tripLocation,
            it.days.map {
                TripDay(
                    it.date.parseAsDate(),
                    it.location,
                    (it.toDo ?: listOf()).map {
                        ToDo(
                            it!!.task,
                            it.isCompleted
                        )
                    },
                    it.notes
                )
            },
            (it.atendees ?: listOf()).map {
                Attendee(
                    it!!.name,
                    it.email,
                    it.phone
                )
            })
    }

fun UpdateTripMutation.Data.toDomain(): Trip =
    this.editUserTrip.let {
        Trip(
            it._id,
            it.userId,
            it.tripName,
            it.tripLocation,
            it.days.map {
                TripDay(
                    it.date.parseAsDate(),
                    it.location,
                    (it.toDo ?: listOf()).map {
                        ToDo(
                            it!!.task,
                            it.isCompleted
                        )
                    },
                    it.notes
                )
            },
            (it.atendees ?: listOf()).map {
                Attendee(
                    it!!.name,
                    it.email,
                    it.phone
                )
            })
    }