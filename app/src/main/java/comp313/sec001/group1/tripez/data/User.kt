package comp313.sec001.group1.tripez.data

import androidx.room.Entity
import androidx.room.PrimaryKey

//User class
@Entity
data class User(
    @PrimaryKey val _id: String,
    val email: String,
    val name: String,
    val phone: String
)
