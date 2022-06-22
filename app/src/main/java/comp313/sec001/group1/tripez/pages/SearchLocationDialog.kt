package comp313.sec001.group1.tripez.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import com.jakewharton.rxbinding4.widget.afterTextChangeEvents
import comp313.sec001.group1.tripez.BuildConfig
import comp313.sec001.group1.tripez.components.dialog.MaybeDialogFragment
import comp313.sec001.group1.tripez.databinding.DialogSearchLocationBinding
import comp313.sec001.group1.tripez.databinding.ItemPlaceResultBinding
import comp313.sec001.group1.tripez.lib.extensions.dpToPixel
import comp313.sec001.group1.tripez.lib.recyclerview.SpacesItemDecoration
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class SearchLocationDialog : MaybeDialogFragment<String>() {
    lateinit var binding: DialogSearchLocationBinding
    private var sub = Disposable.empty()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSearchLocationBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSelect.setOnClickListener {
            onSuccess(binding.tePhrase.text.toString())
        }

        binding.rvPlaceResults.apply {
            adapter = PlaceResultAdapter(emptyList(), ::onSuccess)
            layoutManager = LinearLayoutManager(requireContext(),LinearLayoutManager.VERTICAL,false)
            addItemDecoration(
                SpacesItemDecoration(16.dpToPixel(resources),8.dpToPixel(resources))
            )
        }

        binding.tePhrase.afterTextChangeEvents().skipInitialValue()
            .toFlowable(BackpressureStrategy.LATEST)
            .debounce(DEBOUNCE_MILLIS,TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .map { it.editable.toString() }
            .flatMapSingle(::googlePlaces)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe ({r ->
                binding.rvPlaceResults.apply {
                    (adapter as PlaceResultAdapter).apply {
                        places = r
                        notifyDataSetChanged()
                    }
                }
            },{})
    }

    fun googlePlaces(query: String) =
        Single.create<List<String>> { emitter ->
            Fuel.get(
                "https://maps.googleapis.com/maps/api/place/autocomplete/json",
                listOf(
                    "input" to query,
                    "types" to "establishment",
                    "key" to BuildConfig.GOOGLE_MAPS_KEY
                )
            ).responseObject(GMResponse.Deserializer()).third.let { r ->
                when (r) {
                    is Result.Success -> emitter.onSuccess(r.value.predictions.map { it.description })
                    is Result.Failure -> emitter.onError(r.error)
                }
            }
        }.subscribeOn(Schedulers.io())


    data class GMResponse(val predictions: List<GMResponse.Prediction>) {
        data class Prediction(val description: String)
        class Deserializer : ResponseDeserializable<GMResponse> {
            override fun deserialize(content: String) = Gson().fromJson(content, GMResponse::class.java)
        }
    }

    class PlaceResultAdapter(var places: List<String>, var onClick: (String) -> Unit) : RecyclerView.Adapter<PlaceResultAdapter.ViewHolder>() {
        class ViewHolder(val binding: ItemPlaceResultBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ItemPlaceResultBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ).let(::ViewHolder)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val place = places[position]
            holder.binding.tvPlaceResult.text = place
            holder.binding.root.setOnClickListener {
                onClick(place)
            }
        }

        override fun getItemCount(): Int = places.size
    }

    companion object {
        const val DEBOUNCE_MILLIS: Long = 1000
    }
}