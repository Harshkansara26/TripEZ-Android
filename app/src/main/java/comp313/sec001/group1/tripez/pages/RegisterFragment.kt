package comp313.sec001.group1.tripez.pages

import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.widget.afterTextChangeEvents
import comp313.sec001.group1.tripez.R
import comp313.sec001.group1.tripez.components.fragments.PageFragment
import comp313.sec001.group1.tripez.data.User
import comp313.sec001.group1.tripez.data.form.FormField
import comp313.sec001.group1.tripez.databinding.FragmentRegisterBinding
import comp313.sec001.group1.tripez.lib.Api
import comp313.sec001.group1.tripez.lib.flatten
import comp313.sec001.group1.tripez.viewmodel.BaseStateViewModel
import comp313.sec001.group1.tripez.viewmodel.UserViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable


class RegisterFragment : PageFragment(R.string.register) {

    lateinit var binding: FragmentRegisterBinding
    var sub = Disposable.empty()

    val stateViewModel: StateViewModel by activityViewModels()

    val userViewModel: UserViewModel by activityViewModels()

    override fun onCreateMainContent(inflater: LayoutInflater): View {
        binding = FragmentRegisterBinding.inflate(inflater, null, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialState = stateViewModel.state().blockingFirst()
        binding.etEmail.editText!!.apply {
            setText(initialState.email.value)
            setSelection(initialState.email.value.length)
        }
        binding.etName.editText!!.apply {
            setText(initialState.name.value)
            setSelection(initialState.name.value.length)
        }

        binding.etPhone.editText!!.apply {
            setText(initialState.phone.value)
            setSelection(initialState.phone.value.length)
        }

        binding.etPassword.editText!!.apply {
            setText(initialState.password.value)
            setSelection(initialState.password.value.length)
        }

        val sub1 = stateViewModel.state()
            .distinctUntilChanged()
            .toFlowable(BackpressureStrategy.LATEST)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { s ->
                binding.etEmail.error = when {
                    s.email.touched && s.email.value.isBlank() ->
                        getString(R.string.is_required, getString(R.string.email))
                    else -> null
                }

                binding.etName.error = when {
                    s.name.touched && s.name.value.isBlank() ->
                        getString(R.string.is_required, getString(R.string.name))
                    else -> null
                }

                binding.etPhone.error = when {
                    s.phone.touched && s.phone.value.isBlank() ->
                        getString(R.string.is_required, getString(R.string.phone))
                    else -> null
                }

                binding.etPassword.error = when {
                    s.password.touched && s.password.value.isBlank() ->
                        getString(R.string.is_required, getString(R.string.password))
                    else -> null
                }

                binding.btnRegister.isEnabled =
                    binding.etEmail.error == null && binding.etPassword.error == null &&
                            binding.etName.error == null && binding.etPhone.error == null &&
                            s.email.touched && s.password.touched && s.name.touched && s.phone.touched
            }

        val sub2 = binding.btnNavToLogin.clicks()
            .take(1)
            .subscribe {
                findNavController().apply {
                    navigate(
                        R.id.action_registerFragment_to_loginFragment, null, navOptions {
                            popUpTo(
                                R.id.loginFragment
                            ) {
                                inclusive = true
                            }
                        })

                }
            }

        val sub3 = binding.etEmail.editText!!.afterTextChangeEvents()
            .skipInitialValue()
            .map { it.editable!!.toString() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { txt ->
                stateViewModel.state {
                    it.copy(
                        email = it.email.update(txt)
                    )
                }
            }

        val sub4 = binding.etPassword.editText!!.afterTextChangeEvents()
            .skipInitialValue()
            .map { it.editable!!.toString() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { txt ->
                stateViewModel.state {
                    it.copy(
                        password = it.password.update(txt)
                    )
                }
            }

        val sub5 = binding.etName.editText!!.afterTextChangeEvents()
            .skipInitialValue()
            .map { it.editable!!.toString() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { txt ->
                stateViewModel.state {
                    it.copy(
                        name = it.name.update(txt)
                    )
                }
            }

        val sub6 = binding.etPhone.editText!!.afterTextChangeEvents()
            .skipInitialValue()
            .map { it.editable!!.toString() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { txt ->
                stateViewModel.state {
                    it.copy(
                        phone = it.phone.update(txt)
                    )
                }
            }

        val sub7 =
            binding.btnRegister.clicks().toFlowable(BackpressureStrategy.DROP).flatMapSingle({
                stateViewModel.state().firstOrError().flatMap { state ->
                    Api.instance.register(
                        state.email.value,
                        state.password.value,
                        state.name.value,
                        state.phone.value
                    )
                }.flatten()
            },false,1)
                .take(1)
                .flatMapCompletable {
                    val data = it.createUser
                    userViewModel.user(
                        User(
                            data._id,
                            data.email,
                            data.name,
                            data.phone
                        )
                    )
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    findNavController().navigate(R.id.action_registerFragment_to_home, null,
                        navOptions {
                            popUpTo(R.id.auth_graph) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        })
                }, { Log.e(this@RegisterFragment::class.java.name, it.message, it) })
        sub = CompositeDisposable(
            listOf(
                sub1,
                sub2,
                sub3,
                sub4,
                sub5,
                sub6,
                sub7
            )
        )
    }

    override fun onDestroyView() {
        sub.dispose()
        super.onDestroyView()
    }

    override fun onDestroy() {
        stateViewModel.state { State() }
        super.onDestroy()
    }

    data class State(
        val email: FormField<String> = FormField(""),
        val name: FormField<String> = FormField(""),
        val phone: FormField<String> = FormField(""),
        val password: FormField<String> = FormField("")
    ) {
        fun touch() = copy(
            email = email.copy(touched = true),
            name = name.copy(touched = true),
            phone = phone.copy(touched = true),
            password = password.copy(touched = true)
        )
    }

    class StateViewModel(app: Application) : BaseStateViewModel<State>(State(),app)
}