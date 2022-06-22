package comp313.sec001.group1.tripez.database.dao

import androidx.room.*
import comp313.sec001.group1.tripez.data.Trip
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable

@Dao
interface TripDao {

    @Query("SELECT * FROM trip WHERE _id == :id")
    fun findById(id: String): Maybe<Trip>

    @Query("SELECT * FROM trip")
    fun stream(): Observable<List<Trip>>

    @Insert
    fun insertAll(trips: List<Trip>) : Completable

    @Update
    fun update(trip: Trip) : Completable

    @Insert
    fun insert(trip: Trip) : Completable

    @Delete
    fun delete(trip: Trip) : Completable

    @Query("DELETE FROM trip")
    fun truncate() : Completable
}