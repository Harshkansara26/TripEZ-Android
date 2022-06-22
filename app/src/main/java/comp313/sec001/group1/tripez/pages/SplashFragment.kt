package comp313.sec001.group1.tripez.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import arrow.core.None
import arrow.core.Some
import comp313.sec001.group1.tripez.R
import comp313.sec001.group1.tripez.databinding.FragmentSplashBinding
import comp313.sec001.group1.tripez.viewmodel.UserViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class SplashFragment : Fragment() {

    lateinit var binding: FragmentSplashBinding

    private val userViewModel: UserViewModel by activityViewModels()

    var sub: Disposable = Disposable.empty()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sub = Single.timer(SPLASH_TIME, TimeUnit.MILLISECONDS, Schedulers.io())
            .zipWith(userViewModel.user().firstOrError()){ _, i -> i }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe{u ->
                val action = when (u) {
                    is Some -> R.id.main_graph
                    is None -> R.id.auth_graph
                }
                findNavController().navigate(action, null, navOptions {
                    popUpTo(R.id.splashFragment) {
                        inclusive = true
                    }
                })
            }
    }

    override fun onDestroyView() {
        sub.dispose()
        super.onDestroyView()
    }

    companion object {
        const val SPLASH_TIME: Long = 1000
    }
}