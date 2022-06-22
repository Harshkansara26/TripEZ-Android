package comp313.sec001.group1.tripez.pages

import android.app.Application
import android.os.Bundle
import android.text.InputType
import android.view.*
import androidx.fragment.app.activityViewModels
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.widget.afterTextChangeEvents
import comp313.sec001.group1.tripez.R
import comp313.sec001.group1.tripez.components.fragments.PageFragment
import comp313.sec001.group1.tripez.data.form.FormField
import comp313.sec001.group1.tripez.databinding.FragmentAccountBinding
import comp313.sec001.group1.tripez.lib.Api
import comp313.sec001.group1.tripez.lib.flatten
import comp313.sec001.group1.tripez.viewmodel.BaseStateViewModel
import comp313.sec001.group1.tripez.viewmodel.UserViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Function

class AccountFragment : PageFragment(R.string.account) {

    lateinit var binding: FragmentAccountBinding
    private var actionModeInstance: ActionMode? = null

    val userViewModel: UserViewModel by activityViewModels()
    val stateViewModel: StateViewModel by activityViewModels()

    private var sub = Disposable.empty()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userOption = userViewModel.user().firstOrError().blockingGet()

        userOption.fold({}) { u ->
            stateViewModel.state {
                State(
                    Mode.View,
                    FormField(u.email),
                    FormField(u.name),
                    FormField(u.phone)
                )
            }
        }
    }

    override fun onCreateMainContent(inflater: LayoutInflater): View {
        binding = FragmentAccountBinding.inflate(inflater, null, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Adding the new fixed item
        val editItem = appbar.menu.add(R.string.edit).apply {
            setIcon(R.drawable.ic_edit)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

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

        val sub1 = stateViewModel.state()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { s ->
                //Set up action mode
                when (s.mode) {
                    is Mode.Edit -> {
                        when (actionModeInstance) {
                            null -> {
                                actionModeInstance = appbar.startActionMode(actionModeCallback)
                            }
                            else -> {}
                        }
                        val buttonConfirm = actionModeInstance!!.menu.findItem(R.id.miConfirm)
                        buttonConfirm.isEnabled =
                            binding.etEmail.error == null &&
                                    binding.etName.error == null && binding.etPhone.error == null
                    }
                    is Mode.View -> {
                        when (actionModeInstance) {
                            null -> {}
                            else -> {
                                actionModeInstance!!.finish()
                                actionModeInstance = null
                            }
                        }
                    }
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

                binding.etEmail.editText!!.inputType = InputType.TYPE_NULL
                binding.etName.editText!!.inputType = if (s.mode is Mode.Edit) InputType.TYPE_TEXT_VARIATION_PERSON_NAME else InputType.TYPE_NULL
                binding.etPhone.editText!!.inputType = if (s.mode is Mode.Edit) InputType.TYPE_CLASS_PHONE else InputType.TYPE_NULL
            }

        val sub3 = editItem.clicks()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { _ ->
                stateViewModel.state {
                    it.copy(
                        mode = Mode.Edit(false)
                    )
                }
            }

        val sub4 = binding.etName.editText!!.afterTextChangeEvents()
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

        val sub5 = binding.etPhone.editText!!.afterTextChangeEvents()
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

        val sub6 = stateViewModel.state()
            .distinctUntilChanged(Function { it.mode })
            .filter {
                it.mode is Mode.Edit && it.mode.submitting
            }
            .toFlowable(BackpressureStrategy.DROP)
            .flatMapSingle({ s ->
                userViewModel.userRequired().firstOrError()
                    .flatMap { u ->
                        Api.instance.updateUser(u._id, s.name.value, s.phone.value)
                            .flatten()
                            .map { s.name.value to s.phone.value }
                    }

            }, false, 1)
            //Normally we'd use flatMapCompletable but it is bugged and does not complete
            .flatMapSingle { (name, phone) ->
                userViewModel.user { old ->
                    old.map { u -> u.copy(name = name, phone = phone) }
                }.toSingleDefault(1)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe ({
                stateViewModel.state { it.copy(mode = Mode.View) }
            }, {
                stateViewModel.state { it.copy(mode = Mode.Edit(false)) }
            })

        sub = CompositeDisposable(
            sub1,
            sub3,
            sub4,
            sub5,
            sub6
        )
    }

    override fun onDestroyView() {
        actionModeInstance = null
        sub.dispose()
        super.onDestroyView()

    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.menuInflater.inflate(R.menu.menu_edit, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu?): Boolean {
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.miConfirm -> stateViewModel.state { it.copy(mode = Mode.Edit(true)) }
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            stateViewModel.state { it.copy(mode = Mode.View) }
        }

    }

    sealed class Mode {
        object View : Mode()
        data class Edit(val submitting: Boolean) : Mode()
    }

    data class State(
        val mode: Mode = Mode.View,
        val email: FormField<String> = FormField(""),
        val name: FormField<String> = FormField(""),
        val phone: FormField<String> = FormField(""),
    )

    class StateViewModel(app: Application) : BaseStateViewModel<State>(State(),app)
}