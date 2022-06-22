package comp313.sec001.group1.tripez.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import comp313.sec001.group1.tripez.data.Trip
import comp313.sec001.group1.tripez.database.AppDatabase
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers

class TripViewModel(app: Application) : AndroidViewModel(app) {

    private var tripDao = AppDatabase.instance(app).tripDao()

    fun trips(): Observable<List<Trip>> = tripDao.stream()
    fun trip(id: String): Maybe<Trip> = tripDao.findById(id).subscribeOn(Schedulers.io())

    fun add(trip: Trip): Completable = tripDao.insert(trip).subscribeOn(Schedulers.io())
    fun update(trip: Trip): Completable = tripDao.update(trip).subscribeOn(Schedulers.io())
    fun addAll(trips: List<Trip>): Completable = tripDao.insertAll(trips).subscribeOn(Schedulers.io())

    fun delete(trip: Trip) : Completable = tripDao.delete(trip).subscribeOn(Schedulers.io())
    fun truncate() : Completable = tripDao.truncate().subscribeOn(Schedulers.io()).toSingleDefault(1).ignoreElement()
}