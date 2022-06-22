package comp313.sec001.group1.tripez.database.dao

import androidx.room.*
import comp313.sec001.group1.tripez.data.User
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable

@Dao
interface UserDao {

    @Query("SELECT * FROM user WHERE _id == :id")
    fun findById(id: String): Maybe<User>

    @Query("SELECT * FROM user")
    fun findAll(): Observable<User>

    @Query("SELECT * FROM user LIMIT 1")
    fun user(): Maybe<User>

    @Query("SELECT * FROM user LIMIT 1")
    fun stream(): Observable<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg users: User) : Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: User) : Completable

    @Delete
    fun delete(user: User) : Completable

    @Query("DELETE FROM user")
    fun truncate() : Completable
}