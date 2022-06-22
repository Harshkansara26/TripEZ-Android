package comp313.sec001.group1.tripez.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject

abstract class BaseStateViewModel<T : Any>(protected val default: T, app: Application) :
    AndroidViewModel(app) {
    private var _state = BehaviorSubject.create<T>().toSerialized()

    init {
        _state.onNext(default)
    }

    fun state(): Observable<T> = _state

    fun state(f: (T) -> T) {
        val s = _state.firstElement().blockingGet(default)
        _state.onNext(f(s))
    }

    fun reset() = state { default }

    override fun onCleared() {
        _state.onComplete()
        super.onCleared()
    }
}