package comp313.sec001.group1.tripez.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import arrow.core.*
import comp313.sec001.group1.tripez.data.User
import comp313.sec001.group1.tripez.database.AppDatabase
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers

class UserViewModel(app: Application) : AndroidViewModel(app) {
    private var userDao = AppDatabase.instance(app).userDao()

    fun user(): Observable<Option<User>> = userDao.stream().map { it.firstOrNone() }
    fun userRequired(): Observable<User> =
        user().map { it.getOrElse { throw Exception("User not found!") } }
    fun user(u: User): Completable = user(Some(u))
    fun user(u: Option<User>): Completable =
        userDao.truncate().subscribeOn(Schedulers.io()).andThen(
            when (u) {
                is Some -> userDao.insert(u.value).subscribeOn(Schedulers.io())
                is None -> Completable.complete()
            }
        ).doOnError {
            Log.e(UserViewModel::class.java.name,it.message,it)
        }

    fun user(f: (Option<User>) -> Option<User>) : Completable =
        userDao.user().map{Option(it)}.defaultIfEmpty(None).flatMapCompletable {
            user(f(it))
        }
}