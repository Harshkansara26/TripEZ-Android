package comp313.sec001.group1.tripez.lib

import arrow.core.Either
import com.apollographql.apollo3.ApolloCall
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.*
import comp313.sec001.group1.tripez.*
import comp313.sec001.group1.tripez.type.*
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.rx3.rxSingle

fun <T : Any> Single<Either<List<Error>, T>>.flatten(): Single<T> =
    this.flatMap { e ->
        when (e) {
            is Either.Right -> Single.just(e.value)
            is Either.Left -> Single.error(RuntimeException(e.value.toString()))
        }
    }

class Api private constructor(private val client: ApolloClient) {
    private fun <C : Operation.Data> call(c: ApolloCall<C>) =
        rxSingle { c.execute() }
            .subscribeOn(Schedulers.io())
            .map { r ->
                when {
                    r.hasErrors() -> Either.Left(r.errors!!)
                    else -> Either.Right(r.data!!)
                }
            }

    private fun <T : Mutation.Data> mutation(m: Mutation<T>): Single<Either<List<Error>, T>> =
        call(client.mutation(m))

    private fun <T : Query.Data> query(q: Query<T>): Single<Either<List<Error>, T>> =
        call(client.query(q))

    fun user(id: String): Single<Either<List<Error>, UserQuery.Data>> =
        UserQuery(id).let(::query)

    fun register(email: String, password: String, name: String, phone: String) =
        UserInputData(email, password, name, phone).let(::RegisterMutation).let(::mutation)

    fun login(email: String, password: String): Single<Either<List<Error>, LoginMutation.Data>> =
        LoginParameters(email, password).let(::LoginMutation).let(::mutation)

    fun trip(tripId: String): Single<Either<List<Error>, TripQuery.Data>> =
        TripQuery(tripId).let(::query)

    fun userTrips(userId: String): Single<Either<List<Error>, UserTripsQuery.Data>> =
        UserTripsQuery(userId).let(::query)

    fun createTrip(
        userId: String,
        tripName: String,
        tripLocation: String,
        days: List<InputDays>? = null,
        attendees: List<InputAtendees>? = null
    ): Single<Either<List<Error>, CreateTripMutation.Data>> =
        TripInputData(
            userId,
            tripName,
            tripLocation,
            Optional.presentIfNotNull(days),
            Optional.presentIfNotNull(attendees)
        ).let(::CreateTripMutation).let(::mutation)

    fun updateUser(
        userId: String,
        name: String,
        phone: String
    ): Single<Either<List<Error>, UpdateUserMutation.Data>> =
        UpdateUserMutation(userId, EdituserInputData(name, phone)).let(::mutation)

    fun updateTrip(
        tripId: String,
        userId: String,
        tripName: String,
        tripLocation: String,
        days: List<InputDays>? = null,
        attendees: List<InputAtendees>? = null
    ): Single<Either<List<Error>, UpdateTripMutation.Data>> =
        UpdateTripMutation(
            tripId, TripInputData(
                userId,
                tripName,
                tripLocation,
                Optional.presentIfNotNull(days),
                Optional.presentIfNotNull(attendees)
            )
        ).let(::mutation)

    fun deleteTrip(tripId: String): Single<Either<List<Error>, DeleteTripMutation.Data>> =
        DeleteTripMutation(tripId).let(::mutation)

    companion object {
        val instance: Api by lazy {
            ApolloClient.Builder()
                .serverUrl(BuildConfig.API)
                .build()
                .let(::Api)
        }
    }
}