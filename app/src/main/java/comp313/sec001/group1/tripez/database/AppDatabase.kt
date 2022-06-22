package comp313.sec001.group1.tripez.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import comp313.sec001.group1.tripez.data.Trip
import comp313.sec001.group1.tripez.data.User
import comp313.sec001.group1.tripez.database.dao.TripDao
import comp313.sec001.group1.tripez.database.dao.UserDao

@Database(
    entities = [
        User::class,
        Trip::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun tripDao(): TripDao

    companion object {
        private var _instance : AppDatabase? = null
        fun instance(context: Context): AppDatabase {
            if (_instance == null) {
                _instance = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java, "default-database"
                ).fallbackToDestructiveMigration()
                    .build()
            }
            return _instance!!
        }
    }
}