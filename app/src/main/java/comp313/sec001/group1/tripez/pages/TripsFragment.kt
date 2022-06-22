package comp313.sec001.group1.tripez.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding4.view.clicks
import comp313.sec001.group1.tripez.R
import comp313.sec001.group1.tripez.components.fragments.PageFragment
import comp313.sec001.group1.tripez.data.*
import comp313.sec001.group1.tripez.databinding.FragmentTripsBinding
import comp313.sec001.group1.tripez.databinding.ItemTripBinding
import comp313.sec001.group1.tripez.lib.Api
import comp313.sec001.group1.tripez.lib.flatten
import comp313.sec001.group1.tripez.viewmodel.TripViewModel
import comp313.sec001.group1.tripez.viewmodel.UserViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable


class TripsFragment : PageFragment(R.string.trips) {

    lateinit var binding: FragmentTripsBinding
    val tripViewModel: TripViewModel by activityViewModels()
    val userViewModel: UserViewModel by activityViewModels()
    private var sub = Disposable.empty()
    private var updateSub = Disposable.empty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSub = userViewModel.userRequired().firstOrError().flatMap {
            Api.instance.userTrips(it._id).flatten()
        }.flatMapCompletable {
            tripViewModel.truncate().andThen(
                tripViewModel.addAll(it.toDomain())
            )
        }.subscribe()
    }

    override fun onCreateMainContent(inflater: LayoutInflater): View {
        binding = FragmentTripsBinding.inflate(inflater, null, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvTrips.apply {
            adapter = Adapter(emptyList())
            layoutManager = GridLayoutManager(
                requireContext(), 1
            )
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    (layoutManager as GridLayoutManager).orientation
                )
            )
        }

        val sub1 = tripViewModel.trips()
            .toFlowable(BackpressureStrategy.LATEST)
            .observeOn(AndroidSchedulers.mainThread())
            .switchMap { trips ->
                Flowable.create<Completable>({ emitter ->
                    binding.rvTrips.apply {
                        (adapter as Adapter).apply {
                            tripList = trips
                            onClick = { trip -> emitter.onNext(Completable.fromAction {
                                val args = Bundle().apply {
                                    putString(ViewTripFragment.ID, trip._id)
                                }
                                findNavController().navigate(R.id.action_tripsFragment_to_viewTripFragment,args)
                            }) }
                            onDelete = { _, t ->
                                emitter.onNext(
                                    Api.instance.deleteTrip(t._id).flatten()
                                        .ignoreElement()
                                        .andThen(tripViewModel.delete(t))
                                )
                            }
                            notifyDataSetChanged()
                        }
                    }
                }, BackpressureStrategy.DROP)
            }.flatMapCompletable({ it }, false, 1)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {}

        val sub2 = binding.fabCreateTrip.clicks().take(1)
            .subscribe {
                findNavController().navigate(R.id.action_tripsFragment_to_create_trip_graph)
            }

        sub = CompositeDisposable(sub1, sub2)

    }

    override fun onDestroyView() {
        sub.dispose()
        updateSub.dispose()
        super.onDestroyView()
    }

    class Adapter(
        var tripList: List<Trip>,
        var onClick: (Trip) -> Unit = {},
        var onDelete: (Int, Trip) -> Unit = { _, _ -> }
    ) : RecyclerView.Adapter<Adapter.ViewHolder>() {
        class ViewHolder(val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ItemTripBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ).let(::ViewHolder)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trip = tripList[position]
            val context = holder.itemView.context
            holder.binding.apply {
                tvTripName.text = trip.tripName
                tvTripLocation.text = trip.tripLocation
                tvTripDays.text =
                    context.resources.getString(R.string.n_days, trip.days.size.toString())
                holder.itemView.setOnClickListener { onClick(trip) }
                imgDelete.setOnClickListener {
                    onDelete(position, trip)
                }
            }
        }

        override fun getItemCount(): Int = tripList.size
    }
}