package comp313.sec001.group1.tripez.pages

import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import arrow.core.*
import com.apollographql.apollo3.api.Optional
import com.google.android.material.datepicker.MaterialDatePicker
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
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.time.Instant
import java.util.*


abstract class CreateTripFragment<B : ViewBinding> : PageFragment(R.string.create_trip) {

    val tripViewModel: TripViewModel by activityViewModels()
    val userViewModel: UserViewModel by activityViewModels()
    val stateViewModel: StateViewModel by activityViewModels()

    lateinit var binding: B
    protected var sub: Disposable = Disposable.empty()

    override fun onDestroyView() {
        sub.dispose()
        super.onDestroyView()
    }

    data class State(
        val tripName: FormField<String> = FormField(""),
        val tripLocation: FormField<String> = FormField(""),
        val attendees: List<Attendee> = listOf(),
        val days: List<TripDay> = listOf(),
    )

    class StateViewModel(app: Application) : BaseStateViewModel<State>(State(), app)
}

class CreateTripStep1 : CreateTripFragment<FragmentCreateTripStep1Binding>() {
    override fun onCreateMainContent(inflater: LayoutInflater): View {
        binding = FragmentCreateTripStep1Binding.inflate(inflater, null, false)
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

        val sub1 = stateViewModel.state()
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

                binding.btnDateRange.text = when {
                    s.days.isEmpty() -> getString(R.string.click_to_select)
                    else -> {
                        val start = s.days.first().date.format()
                        val end = s.days.last().date.format()
                        "$start - $end"
                    }
                }.let { str -> getString(R.string.trip_period, str) }

                binding.btnNext.isEnabled =
                    binding.etTripName.error == null && binding.etTripLocation.error == null &&
                            s.tripName.touched && s.tripLocation.touched && s.days.isNotEmpty()
            }

        val sub2 = binding.btnDateRange.clicks()
            .toFlowable(BackpressureStrategy.DROP)
            .flatMapMaybe({ rxDatePicker() }, false, 1)
            .subscribe { ds ->
                stateViewModel.state { s ->
                    s.copy(days = updateDays(s.days, ds))
                }
            }

        val sub3 = binding.etTripName.editText!!.afterTextChangeEvents()
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

        val sub4 = binding.etTripLocation.editText!!.afterTextChangeEvents()
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

        val sub5 = binding.btnNext.clicks()
            .take(1)
            .subscribe {
                findNavController().navigate(R.id.action_createTripStep1_to_createTripStep2)
            }

        sub = CompositeDisposable(sub1, sub2, sub3, sub4, sub5)
    }

    private fun rxDatePicker() = stateViewModel.state().firstOrError()
        .map { it.days.map(TripDay::date) }
        .flatMapMaybe { days ->
            val (start, end) = when {
                days.isEmpty() -> Instant.now().let { it to it.plusMillis(DEFAULT_DAYS_MILLIS) }
                else -> days.first().toInstant() to days.last().toInstant()
            }
            Maybe.create<List<Date>> { emitter ->
                MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(R.string.select_dates)
                    .setSelection(
                        androidx.core.util.Pair(
                            start.toEpochMilli(),
                            end.toEpochMilli()
                        )
                    )
                    .build().apply {
                        addOnCancelListener { emitter.onComplete() }
                        addOnNegativeButtonClickListener { emitter.onComplete() }
                        addOnPositiveButtonClickListener {
                            emitter.onSuccess(
                                allDays(
                                    Date(it.first).toInstant(),
                                    Date(it.second).toInstant()
                                )
                            )
                        }
                        show(this@CreateTripStep1.parentFragmentManager, "datepicker")
                    }
            }.subscribeOn(AndroidSchedulers.mainThread())
        }

    private fun allDays(start: Instant, end: Instant): List<Date> {
        val fills = mutableListOf<Instant>()
        var pointer = start
        while (end.minusMillis(pointer.toEpochMilli()).toEpochMilli() > DAY_MILLIS) {
            pointer = pointer.plusMillis(DAY_MILLIS)
            fills.add(pointer)
        }
        return (listOf(start) + fills + listOf(end)).map { Date(it.toEpochMilli()) }
    }

    private fun updateDays(tripDays: List<TripDay>, days: List<Date>): List<TripDay> {
        return days.map { d ->
            tripDays.firstOrNone { it.date == d }
                .getOrElse { TripDay(d, listOf(), listOf(), null) }
        }
    }

    companion object {
        const val DAY_MILLIS: Long = 1000 * 60 * 60 * 24
        const val DEFAULT_DAYS_MILLIS: Long = 2 * DAY_MILLIS
    }
}

class CreateTripStep2 : CreateTripFragment<FragmentCreateTripStep2Binding>() {
    override fun onCreateMainContent(inflater: LayoutInflater): View {
        binding = FragmentCreateTripStep2Binding.inflate(inflater, null, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialState = stateViewModel.state().blockingFirst()

        binding.rvAttendees.apply {
            adapter =
                AttendeeAdapter(
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

        val sub3 =
            binding.btnCreate.clicks().toFlowable(BackpressureStrategy.DROP)
                .flatMapSingle({
                    stateViewModel.state().firstOrError()
                        .zipWith(userViewModel.userRequired().firstOrError()) { l, r -> l to r }
                        .flatMap { (state, user) ->
                            Api.instance.createTrip(
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
                .firstOrError()
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap { r ->
                    tripViewModel.add(r.toDomain()).toSingleDefault(1)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    stateViewModel.reset()
                    findNavController().popBackStack(R.id.create_trip_graph, true)
                }, { Log.e(this::class.java.name, it.message, it) })

        sub = CompositeDisposable(sub3)
    }

    abstract class BaseAppendableAdapter<A, D : ViewBinding, B : ViewBinding>(
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

        override fun getItemCount(): Int = inputList.size + 1

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
        override var inputList: List<Attendee>,
        override var onAdd: (Int) -> Maybe<Attendee>,
        override var onDelete: (Int, Attendee) -> Unit,
        override var update: (Int, (Attendee) -> Attendee) -> Unit,
    ) : BaseAppendableAdapter<Attendee, ItemAddItemBinding, ItemAttendeeBinding>(
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
                setText(attendee.email)
                setSelection(attendee.email?.length ?: 0)
            }
            binding.etName.editText?.apply {
                setText(attendee.name)
                setSelection(attendee.name?.length ?: 0)
            }
            binding.etPhone.editText?.apply {
                setText(attendee.phone)
                setSelection(attendee.phone?.length ?: 0)
            }
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
        override var inputList: List<TripDay>,
        override var onAdd: (Int) -> Maybe<TripDay>,
        override var onDelete: (Int, TripDay) -> Unit,
        override var update: (Int, (TripDay) -> TripDay) -> Unit,
    ) : BaseAppendableAdapter<TripDay, ItemAddItemBinding, ItemTripDayBinding>(
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
                setText(tripDay.notes)
                setSelection(tripDay.notes?.length ?: 0)
            }
            binding.rvTodos.apply {
                adapter = ToDoAdapter(
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
            holder.binding.cbDone.isChecked = todo.isCompleted
            holder.binding.tvTodo.setText(todo.task)
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
        override var inputList: List<String>,
        override var onAdd: (Int) -> Maybe<String>,
        override var onDelete: (Int, String) -> Unit,
        override var update: (Int, (String) -> String) -> Unit,
    ) : BaseAppendableAdapter<String, ItemAddLocationChipBinding, ItemLocationBinding>(
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
            binding.chipLocation.text = loc
            binding.chipLocation.setOnCloseIconClickListener {
                deleteItem(position)
            }
        }

    }
}