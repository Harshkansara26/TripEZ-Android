package comp313.sec001.group1.tripez.components.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import android.view.Window
import comp313.sec001.group1.tripez.R
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.subjects.MaybeSubject

abstract class MaybeDialogFragment<T> : androidx.fragment.app.DialogFragment() {
    private val result: MaybeSubject<T> = MaybeSubject.create()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
//            requestWindowFeature(Window.FEATURE_NO_TITLE)
//            window!!.setLayout(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT
//            )
        }
    }

    override fun getTheme(): Int {
        return R.style.FullDialog
    }

    protected fun onSuccess(t: T) {
        result.onSuccess(t)
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        result.onComplete()
        super.onDismiss(dialog)
    }

    public fun maybe(manager: FragmentManager, tag: String?): Maybe<T> {
        return Completable.fromAction {
            show(manager, tag)
            manager.executePendingTransactions()
        }.subscribeOn(AndroidSchedulers.mainThread())
            .andThen(result)
    }
}