package comp313.sec001.group1.tripez.components.fragments

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainer
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import arrow.core.None
import arrow.core.Some
import com.google.android.material.appbar.MaterialToolbar
import com.jakewharton.rxbinding4.view.clicks
import comp313.sec001.group1.tripez.R
import comp313.sec001.group1.tripez.databinding.FragmentPageBinding
import comp313.sec001.group1.tripez.viewmodel.UserViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable

abstract class PageFragment(@StringRes private val titleId: Int) : Fragment() {

    private lateinit var binding: FragmentPageBinding
    private var sub: Disposable = Disposable.empty()
    private val userViewModel: UserViewModel by activityViewModels()
    lateinit var appbar: MaterialToolbar

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): ViewGroup {
        binding = FragmentPageBinding.inflate(inflater, container, false)
        val children = onCreateMainContent(inflater)
        binding.mainContent.addView(children)
        return binding.root
    }

    abstract fun onCreateMainContent(inflater: LayoutInflater): View?

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appbar = binding.topAppBar
        appbar.title = resources.getString(titleId)
        sub = userViewModel.user()
            .toFlowable(BackpressureStrategy.LATEST)
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapCompletable({ u ->
                val account = appbar.menu.findItem(R.id.miAccount)
                val logout = appbar.menu.findItem(R.id.miLogout)
                val home = appbar.menu.findItem(R.id.miHome)
                when (u) {
                    is Some -> {
                        val comp1 = account.clicks().flatMapCompletable {
                            Completable.fromAction {
                                findNavController().navigate(
                                    R.id.accountFragment,
                                    null,
                                    navOptions {
                                        launchSingleTop = true
                                    })
                            }
                        }
                        val comp2 = logout.clicks().take(1).flatMapCompletable {
                            userViewModel.user(None)
                        }
                            .observeOn(AndroidSchedulers.mainThread())
                            .andThen(
                                Completable.fromAction {
                                    val nav = findNavController()
                                    nav.navigate(R.id.nav_graph,
                                        null,
                                        navOptions {
                                            popUpTo(R.id.nav_graph) {
                                                inclusive = true
                                            }
                                        })
                                }
                            )
                        val comp3 = home.clicks().flatMapCompletable {
                            Completable.fromAction {
                                findNavController().navigate(
                                    R.id.main_graph,
                                    null,
                                    navOptions {
                                        launchSingleTop = true
                                    })
                            }
                        }
                        Completable.fromAction {
                            account.isVisible = true
                            logout.isVisible = true
                            home.isVisible = true
                        }.andThen(
                            Completable.merge(
                                listOf(
                                    comp1,
                                    comp2,
                                    comp3
                                )
                            )
                        )
                    }
                    is None -> Completable.fromAction {
                        account.isVisible = false
                        logout.isVisible = false
                        home.isVisible = false
                    }
                }
            },false,1).subscribe()
    }

    override fun onDestroyView() {
        sub.dispose()
        super.onDestroyView()
    }
}