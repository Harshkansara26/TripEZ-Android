package comp313.sec001.group1.tripez.pages

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import arrow.core.Either
import arrow.core.merge
import com.apollographql.apollo3.api.Optional
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.gson.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.widget.afterTextChangeEvents
import com.jakewharton.rxbinding4.widget.checkedChanges
import comp313.sec001.group1.tripez.R
import comp313.sec001.group1.tripez.components.fragments.PageFragment
import comp313.sec001.group1.tripez.data.*
import comp313.sec001.group1.tripez.data.form.FormField
import comp313.sec001.group1.tripez.databinding.*
import comp313.sec001.group1.tripez.lib.Api
import comp313.sec001.group1.tripez.lib.extensions.dpToPixel
import comp313.sec001.group1.tripez.lib.extensions.format
import comp313.sec001.group1.tripez.lib.flatten
import comp313.sec001.group1.tripez.lib.recyclerview.SpacesItemDecoration
import comp313.sec001.group1.tripez.type.InputAtendees
import comp313.sec001.group1.tripez.type.InputDays
import comp313.sec001.group1.tripez.type.InputToDo
import comp313.sec001.group1.tripez.viewmodel.BaseStateViewModel
import comp313.sec001.group1.tripez.viewmodel.TripViewModel
import comp313.sec001.group1.tripez.viewmodel.UserViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.*


class ViewTripFragment : PageFragment(R.string.view_trip) {

    val tripViewModel: TripViewModel by activityViewModels()
    val userViewModel: UserViewModel by activityViewModels()
    val stateViewModel: StateViewModel by activityViewModels()
    private var actionModeInstance: ActionMode? = null

    lateinit var binding: FragmentViewTripBinding
    protected var sub: Disposable = Disposable.empty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = requireArguments().getString(ID)!!
        val trip = tripViewModel.trip(id).toSingle().blockingGet()
        stateViewModel.state {
            State(
                trip,
                Mode.View,
                trip._id,
                FormField(trip.tripName),
                FormField(trip.tripLocation),
                trip.atendees,
                trip.days
            )
        }

    }

    override fun onCreateMainContent(inflater: LayoutInflater): View? {
        binding = FragmentViewTripBinding.inflate(inflater, null, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialState = stateViewModel.state().blockingFirst()

        binding.etTripName.editText!!.apply {
            setText(initialState.tripName.value)
            setSelection(initialState.tripName.value.length)
        }
        binding.etTripLocation.editText!!.apply {
            setText(initialState.tripLocation.value)
            setSelection(initialState.tripLocation.value.length)
        }

        binding.rvAttendees.apply {
            adapter =
                AttendeeAdapter(
                    initialState.mode,
                    initialState.attendees,
                    onDelete = { i, _ ->
                        stateViewModel.state {
                            it.copy(attendees = it.attendees.filterIndexed { idx, _ -> idx != i })
                        }
                    },
                    onAdd = { i ->
                        val attendee = Attendee("", "", "")
                        stateViewModel.state {
                            it.copy(attendees = it.attendees + attendee)
                        }
                        Maybe.just(attendee)
                    },
                    update = { i, f ->
                        stateViewModel.state { st ->
                            st.copy(attendees = st.attendees.mapIndexed { index, attendee ->
                                if (index == i) f(attendee) else attendee
                            })
                        }
                    }
                )
            layoutManager = GridLayoutManager(
                requireContext(),
                1,
                GridLayoutManager.HORIZONTAL,
                false
            )
            addItemDecoration(SpacesItemDecoration(16.dpToPixel(resources)))
        }

        binding.rvTripDays.apply {
            adapter = TripDayAdapter(
                parentFragmentManager,
                initialState.mode,
                initialState.days,
                onDelete = { i, _ ->
                    stateViewModel.state {
                        it.copy(days = it.days.filterIndexed { idx, _ -> idx != i })
                    }
                },
                onAdd = { i ->
                    val c = Calendar.getInstance()
                    val last = stateViewModel.state().blockingFirst().days.last()
                    c.time = last.date
                    c.add(Calendar.DATE, 1)
                    val day = TripDay(
                        c.time,
                        emptyList(),
                        emptyList(),
                        ""
                    )
                    stateViewModel.state {
                        it.copy(
                            days = it.days + day
                        )
                    }
                    Maybe.just(day)
                },
                update = { i, f ->
                    stateViewModel.state { st ->
                        st.copy(days = st.days.mapIndexed { index, day ->
                            if (index == i) f(day) else day
                        })
                    }
                }
            )
            layoutManager = GridLayoutManager(
                requireContext(),
                1,
                GridLayoutManager.HORIZONTAL,
                false
            )
            addItemDecoration(SpacesItemDecoration(16.dpToPixel(resources)))
        }

        val editItem = appbar.menu.add(R.string.edit).apply {
            setIcon(R.drawable.ic_edit)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        val downloadItem = appbar.menu.add(R.string.download).apply {
            setIcon(R.drawable.ic_download)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        val sub1 = stateViewModel.state()
            .distinctUntilChanged(Function { Triple(it.tripName, it.tripLocation, it.mode) })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { s ->
                binding.etTripName.error = when {
                    s.tripName.touched && s.tripName.value.isBlank() ->
                        getString(R.string.is_required, getString(R.string.trip_name))
                    else -> null
                }

                binding.etTripLocation.error = when {
                    s.tripLocation.touched && s.tripLocation.value.isBlank() ->
                        getString(R.string.is_required, getString(R.string.trip_location))
                    else -> null
                }

                actionModeInstance?.menu?.findItem(R.id.miConfirm)?.apply {
                    isEnabled =
                        binding.etTripName.error == null && binding.etTripLocation.error == null
                }
            }

        val sub2 = binding.etTripName.editText!!.afterTextChangeEvents()
            .skipInitialValue()
            .map { it.editable!!.toString() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { txt ->
                stateViewModel.state {
                    it.copy(
                        tripName = it.tripName.update(txt)
                    )
                }
            }

        val sub3 = binding.etTripLocation.editText!!.afterTextChangeEvents()
            .skipInitialValue()
            .map { it.editable!!.toString() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { txt ->
                stateViewModel.state {
                    it.copy(
                        tripLocation = it.tripLocation.update(txt)
                    )
                }
            }

        val sub4 = editItem.clicks()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { _ ->
                stateViewModel.state {
                    it.copy(
                        mode = Mode.Edit(false)
                    )
                }
            }

        val sub5 =
            stateViewModel.state()
                .distinctUntilChanged(Function { it.mode })
                .filter {
                    it.mode is Mode.Edit && it.mode.submitting
                }
                .toFlowable(BackpressureStrategy.DROP)
                .flatMapSingle({
                    stateViewModel.state().firstOrError()
                        .zipWith(userViewModel.userRequired().firstOrError()) { l, r -> l to r }
                        .flatMap { (state, user) ->
                            Api.instance.updateTrip(
                                state.tripId,
                                user._id,
                                state.tripName.value,
                                state.tripLocation.value,
                                state.days.map {
                                    InputDays(
                                        it.date.format(),
                                        Optional.Present(it.locations),
                                        Optional.Present(it.toDos.map {
                                            InputToDo(
                                                it.task,
                                                it.isCompleted
                                            )
                                        })
                                    )
                                },
                                state.attendees.map {
                                    InputAtendees(
                                        it.name!!,
                                        it.email!!,
                                        it.phone!!
                                    )
                                }
                            ).flatten()
                        }
                }, false, 1)
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapSingle { r ->
                    val trip = r.toDomain()
                    tripViewModel.update(trip).toSingleDefault(trip)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({trip ->
                    stateViewModel.state { it.copy(trip = trip, mode = Mode.View) }
                }, {
                    Log.e(this::class.java.name, it.message, it)
                    stateViewModel.state { it.copy(mode = Mode.Edit(false)) }
                })

        val sub6 = stateViewModel.state()
            .observeOn(AndroidSchedulers.mainThread())
            .map { it.tripName }
            .distinctUntilChanged()
            .subscribe { name ->
                appbar.title = name.value
            }

        val sub7 = stateViewModel.state()
            .observeOn(AndroidSchedulers.mainThread())
            .map { it.mode }
            .distinctUntilChanged()
            .subscribe { m ->
                when (m) {
                    is Mode.Edit -> {
                        when (actionModeInstance) {
                            null -> {
                                actionModeInstance = appbar.startActionMode(actionModeCallback)
                                    .apply {
                                        menu.findItem(R.id.miConfirm).isEnabled =
                                            binding.etTripName.error == null && binding.etTripLocation.error == null
                                    }
                            }
                            else -> {}
                        }
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
                binding.etTripName.editText!!.inputType =
                    if (m == Mode.View) InputType.TYPE_NULL else InputType.TYPE_CLASS_TEXT
                binding.etTripLocation.editText!!.inputType =
                    if (m == Mode.View) InputType.TYPE_NULL else InputType.TYPE_CLASS_TEXT
                binding.rvAttendees.apply {
                    (adapter as AttendeeAdapter).apply {
                        mode = m
                        notifyDataSetChanged()
                    }
                }

                binding.rvTripDays.apply {
                    (adapter as TripDayAdapter).apply {
                        mode = m
                        notifyDataSetChanged()
                    }
                }
            }
        val sub8 = downloadItem.clicks()
            .toFlowable(BackpressureStrategy.DROP)
            .flatMapSingle({
                stateViewModel.state().firstOrError().map { it.trip }.flatMap {trip ->
                    Single.create<String> { emitter ->
                        PDF_URL
                            .httpPost()
                            .jsonBody(trip)
                            .timeout(30000)
                            .responseObject(PDFResponse.Deserializer()).third
                            .let { r ->
                                when (r) {
                                    is Result.Success -> emitter.onSuccess(r.value.download_url)
                                    is Result.Failure -> emitter.onError(r.error)
                                }
                            }
                    }.subscribeOn(Schedulers.io())
                }
            }, false, 1)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ url ->
                Intent(Intent.ACTION_VIEW,Uri.parse(url)).let(::startActivity)
            }, { Log.e(this@ViewTripFragment::class.java.name, it.message, it) })
        sub = CompositeDisposable(sub1, sub2, sub3, sub4, sub5, sub6, sub7, sub8)
    }

    abstract class BaseAppendableAdapter<A, D : ViewBinding, B : ViewBinding>(
        open var mode: Mode,
        open var inputList: List<A>,
        open var onAdd: (Int) -> Maybe<A>,
        open var onDelete: (Int, A) -> Unit,
        open var update: (Int, (A) -> A) -> Unit,
        private val widthUtilizationPct: Float = 0.8f
    ) : RecyclerView.Adapter<BaseAppendableAdapter.ViewHolder<D, B>>() {
        class ViewHolder<D : ViewBinding, B : ViewBinding>(val binding: Either<D, B>) :
            RecyclerView.ViewHolder(binding.merge().root) {
            var sub = CompositeDisposable()
        }

        protected fun updateItem(position: Int, updater: (A) -> A) {
            update(position, updater)
            inputList = inputList.mapIndexed { i, x -> if (i == position) updater(x) else x }
        }

        protected fun deleteItem(position: Int) {
            onDelete(position, inputList[position])
            inputList = inputList.filterIndexed { i, _ -> i != position }
            notifyDataSetChanged()
        }

        protected fun addItem(position: Int): Completable {
            return onAdd(position)
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapCompletable { item ->
                    Completable.fromAction {
                        inputList = inputList + item
                        notifyDataSetChanged()
                    }
                }
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == inputList.size) VIEW_TYPE_ADD else VIEW_TYPE_ITEM
        }

        override fun getItemCount(): Int = when (mode) {
            is Mode.Edit -> inputList.size + 1
            else -> inputList.size
        }

        companion object {
            const val VIEW_TYPE_ADD = 0
            const val VIEW_TYPE_ITEM = 1
        }

        abstract fun onCreateBinding(parent: ViewGroup): B
        abstract fun onCreateAddBinding(parent: ViewGroup): D

        final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<D, B> {
            return when (viewType) {
                VIEW_TYPE_ITEM -> {
                    onCreateBinding(parent).apply {
                        when (widthUtilizationPct) {
                            0f -> {}
                            else -> {
                                when (root.layoutParams) {
                                    null -> {
                                        root.layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                    else -> {}
                                }
                                root.layoutParams.apply {
                                    width = (parent.width.toFloat() * widthUtilizationPct).toInt()
                                }.let(root::setLayoutParams)
                            }
                        }
                    }.let { Either.Right(it) }
                }
                else -> onCreateAddBinding(parent)
                    .let { Either.Left(it) }
            }.let(::ViewHolder)
        }

        abstract fun onBindBinding(binding: B, sub: CompositeDisposable, position: Int)

        final override fun onBindViewHolder(holder: ViewHolder<D, B>, position: Int) {
            holder.sub.clear()
            when (holder.binding) {
                is Either.Right -> onBindBinding(holder.binding.value, holder.sub, position)
                is Either.Left -> {
                    holder.sub.add(
                        holder.binding.value.root.clicks()
                            .toFlowable(BackpressureStrategy.DROP)
                            .flatMapCompletable { addItem(position) }
                            .subscribe()
                    )
                }
            }
        }
    }

    class AttendeeAdapter(
        override var mode: Mode,
        override var inputList: List<Attendee>,
        override var onAdd: (Int) -> Maybe<Attendee>,
        override var onDelete: (Int, Attendee) -> Unit,
        override var update: (Int, (Attendee) -> Attendee) -> Unit,
    ) : BaseAppendableAdapter<Attendee, ItemAddItemBinding, ItemAttendeeBinding>(
        mode,
        inputList,
        onAdd,
        onDelete,
        update
    ) {

        override fun onCreateAddBinding(parent: ViewGroup): ItemAddItemBinding {
            return ItemAddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }

        override fun onCreateBinding(parent: ViewGroup): ItemAttendeeBinding {
            return ItemAttendeeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }

        override fun onBindBinding(
            binding: ItemAttendeeBinding,
            sub: CompositeDisposable,
            position: Int
        ) {
            val attendee = inputList[position]
            binding.etEmail.editText?.apply {
                inputType =
                    if (mode == Mode.View) InputType.TYPE_NULL else InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                setText(attendee.email)
                setSelection(attendee.email?.length ?: 0)
            }
            binding.etName.editText?.apply {
                inputType =
                    if (mode == Mode.View) InputType.TYPE_NULL else InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                setText(attendee.name)
                setSelection(attendee.name?.length ?: 0)
            }
            binding.etPhone.editText?.apply {
                inputType =
                    if (mode == Mode.View) InputType.TYPE_NULL else InputType.TYPE_CLASS_PHONE
                setText(attendee.phone)
                setSelection(attendee.phone?.length ?: 0)
            }
            binding.btnDelAttendee.visibility = if (mode == Mode.View) View.GONE else View.VISIBLE
            sub.add(
                binding.etEmail.editText!!.afterTextChangeEvents().skipInitialValue()
                    .map { it.editable!!.toString() }
                    .distinctUntilChanged()
                    .subscribe {
                        updateItem(position) { s -> s.copy(email = it) }
                    }
            )
            sub.add(
                binding.etName.editText!!.afterTextChangeEvents().skipInitialValue()
                    .map { it.editable!!.toString() }
                    .distinctUntilChanged()
                    .subscribe {
                        updateItem(position) { s -> s.copy(name = it) }
                    }
            )
            sub.add(
                binding.etPhone.editText!!.afterTextChangeEvents().skipInitialValue()
                    .map { it.editable!!.toString() }
                    .distinctUntilChanged()
                    .subscribe {
                        updateItem(position) { s -> s.copy(phone = it) }
                    }
            )
            sub.add(
                binding.btnDelAttendee.clicks().subscribe {
                    deleteItem(position)
                }
            )
        }

    }

    class TripDayAdapter(
        private var parentFragmentManager: FragmentManager,
        override var mode: Mode,
        override var inputList: List<TripDay>,
        override var onAdd: (Int) -> Maybe<TripDay>,
        override var onDelete: (Int, TripDay) -> Unit,
        override var update: (Int, (TripDay) -> TripDay) -> Unit,
    ) : BaseAppendableAdapter<TripDay, ItemAddItemBinding, ItemTripDayBinding>(
        mode,
        inputList,
        onAdd,
        onDelete,
        update
    ) {
        override fun onCreateAddBinding(parent: ViewGroup): ItemAddItemBinding {
            return ItemAddItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }

        override fun onCreateBinding(parent: ViewGroup): ItemTripDayBinding {
            return ItemTripDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        }

        override fun onBindBinding(
            binding: ItemTripDayBinding,
            sub: CompositeDisposable,
            position: Int
        ) {
            val context = binding.root.context
            val tripDay = inputList[position]
            binding.tvDayNum.text = context.getString(R.string.day_num, (position + 1).toString())
            binding.tvDate.text = context.getString(R.string.date, tripDay.date.format())
            binding.etNotes.editText!!.apply {
                isEnabled = mode != Mode.View
                setText(tripDay.notes)
                setSelection(tripDay.notes?.length ?: 0)
            }
            binding.chipAddToDo.apply {
                visibility = if (mode == Mode.View) View.GONE else View.VISIBLE
            }
            binding.rvTodos.apply {
                adapter = ToDoAdapter(
                    mode,
                    tripDay.toDos,
                    { p ->
                        updateItem(position) { d ->
                            d.copy(toDos = d.toDos.filterIndexed { i, _ -> i != p })
                        }
                    },
                    { i, f ->
                        updateItem(position) { d ->
                            d.copy(toDos = d.toDos.mapIndexed { idx, td ->
                                if (idx == i) f(td) else td
                            })
                        }
                    })
                layoutManager = GridLayoutManager(context, 1, GridLayoutManager.VERTICAL, false)
                when (itemDecorationCount) {
                    0 -> addItemDecoration(
                        SpacesItemDecoration(
                            16.dpToPixel(resources),
                            4.dpToPixel(resources)
                        )
                    )
                }
            }

            binding.rvLocations.apply {
                adapter = LocationAdapter(
                    mode,
                    tripDay.locations,
                    onDelete = { p, _ ->
                        updateItem(position) { d ->
                            d.copy(locations = d.locations.filterIndexed { i, _ -> i != p })
                        }
                    },
                    update = { i, f ->
                        updateItem(position) { d ->
                            d.copy(locations = d.locations.mapIndexed { idx, td ->
                                if (idx == i) f(td) else td
                            })
                        }
                    },
                    onAdd = { p ->
                        SearchLocationDialog()
                            .maybe(parentFragmentManager, null)
                            .observeOn(AndroidSchedulers.mainThread())
                            .flatMap {
                                Completable.fromAction {
                                    updateItem(position) { d ->
                                        d.copy(locations = d.locations + it)
                                    }
                                }.subscribeOn(AndroidSchedulers.mainThread())
                                    .andThen(Maybe.just(it))
                            }
                    })
                layoutManager =
                    GridLayoutManager(context, 1, GridLayoutManager.HORIZONTAL, false)
                when (itemDecorationCount) {
                    0 -> addItemDecoration(
                        SpacesItemDecoration(
                            16.dpToPixel(resources),
                            4.dpToPixel(resources)
                        )
                    )
                }
            }

            sub.add(
                binding.etNotes.editText!!.afterTextChangeEvents()
                    .skipInitialValue()
                    .toFlowable(BackpressureStrategy.LATEST)
                    .map { it.editable!!.toString() }
                    .subscribe {
                        updateItem(position) { d -> d.copy(notes = it) }
                    }
            )

            sub.add(
                binding.chipAddToDo.clicks().toFlowable(BackpressureStrategy.DROP)
                    .subscribe {
                        val newTodo = ToDo("", false)
                        updateItem(position) { d -> d.copy(toDos = d.toDos + newTodo) }
                        (binding.rvTodos.adapter as ToDoAdapter).apply {
                            todos = todos + newTodo
                            notifyDataSetChanged()
                        }
                    }
            )
        }
    }

    class ToDoAdapter(
        var mode: Mode,
        var todos: List<ToDo>,
        private var onDelete: (Int) -> Unit,
        private var onUpdate: (Int, (ToDo) -> ToDo) -> Unit
    ) :
        RecyclerView.Adapter<ToDoAdapter.ViewHolder>() {
        class ViewHolder(val binding: ItemTodoBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ItemTodoBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ).let(::ViewHolder)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val todo = todos[position]
            holder.binding.cbDone.apply {
                isEnabled = mode != Mode.View
                isChecked = todo.isCompleted
            }
            holder.binding.tvTodo.apply {
                setText(todo.task)
                inputType =
                    if (mode == Mode.View) InputType.TYPE_NULL else InputType.TYPE_CLASS_TEXT
            }
            holder.binding.btnDelTodo.apply {
                visibility = if (mode == Mode.View) View.GONE else View.VISIBLE
            }
            holder.binding.btnDelTodo.setOnClickListener {
                onDelete(position)
                todos = todos.filterIndexed { i, _ -> i != position }
                notifyDataSetChanged()
            }

            holder.binding.cbDone.checkedChanges().skipInitialValue()
                .toFlowable(BackpressureStrategy.LATEST)
                .subscribe {
                    val f: (ToDo) -> ToDo = { td -> td.copy(isCompleted = it) }
                    onUpdate(position, f)
                    todos = todos.mapIndexed { i, toDo -> if (i == position) f(toDo) else toDo }
                }

            holder.binding.tvTodo.afterTextChangeEvents().skipInitialValue()
                .toFlowable(BackpressureStrategy.DROP)
                .map { it.editable.toString() }
                .subscribe {
                    val f: (ToDo) -> ToDo = { td -> td.copy(task = it) }
                    onUpdate(position, f)
                    todos = todos.mapIndexed { i, toDo -> if (i == position) f(toDo) else toDo }
                }
        }

        override fun getItemCount(): Int = todos.size
    }

    class LocationAdapter(
        override var mode: Mode,
        override var inputList: List<String>,
        override var onAdd: (Int) -> Maybe<String>,
        override var onDelete: (Int, String) -> Unit,
        override var update: (Int, (String) -> String) -> Unit,
    ) : BaseAppendableAdapter<String, ItemAddLocationChipBinding, ItemLocationBinding>(
        mode,
        inputList,
        onAdd,
        onDelete,
        update,
        0f
    ) {
        override fun onCreateBinding(parent: ViewGroup): ItemLocationBinding {
            return ItemLocationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        }

        override fun onCreateAddBinding(parent: ViewGroup): ItemAddLocationChipBinding {
            return ItemAddLocationChipBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        }

        override fun onBindBinding(
            binding: ItemLocationBinding,
            sub: CompositeDisposable,
            position: Int
        ) {
            val loc = inputList[position]
            binding.chipLocation.apply {
                text = loc
                isCloseIconVisible = mode != Mode.View
                setOnCloseIconClickListener {
                    deleteItem(position)
                }
            }
        }

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
        val trip: Trip =Trip("","","","", listOf(), listOf()),
        val mode: Mode = Mode.View,
        val tripId: String = "",
        val tripName: FormField<String> = FormField(""),
        val tripLocation: FormField<String> = FormField(""),
        val attendees: List<Attendee> = listOf(),
        val days: List<TripDay> = listOf(),
    )

    class StateViewModel(app: Application) : BaseStateViewModel<State>(State(), app)

    data class PDFResponse(val download_url: String) {
        class Deserializer : ResponseDeserializable<PDFResponse> {
            override fun deserialize(content: String) =
                Gson().fromJson(content, PDFResponse::class.java)
        }
    }

    companion object {
        const val PDF_URL = "http://tripez-service.herokuapp.com/pdf"
        const val ID = "id"
    }
}