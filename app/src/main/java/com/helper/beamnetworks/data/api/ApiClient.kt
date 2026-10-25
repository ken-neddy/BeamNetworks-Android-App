package com.helper.beamnetworks.data.api

import com.helper.beamnetworks.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Builds the Retrofit client: bearer token on every call, one serialised refresh on 401. */
class ApiClient(private val sessions: SessionStore) {
    private val jsonType = "application/json".toMediaType()
    private val refreshLock = Any()

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    private val authInterceptor = Interceptor { chain ->
        val token = sessions.current?.access
        val request = chain.request()
        chain.proceed(
            if (token != null && request.header("Authorization") == null) {
                request.newBuilder().header("Authorization", "Bearer $token").build()
            } else request,
        )
    }

    /**
     * Refresh tokens rotate and may only be used once (API.md §2.1), so refreshes are serialised;
     * a request that failed with an older token just retries with the newer one.
     */
    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.url.encodedPath.contains("/auth/")) return null
            if (responseCount(response) >= 2) return null
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            synchronized(refreshLock) {
                val session = sessions.current ?: return null
                if (session.access != failedToken) {
                    return response.request.newBuilder().header("Authorization", "Bearer ${session.access}").build()
                }
                return when (val result = refresh(session.refresh)) {
                    is RefreshResult.Success -> {
                        sessions.updateTokens(result.tokens.access, result.tokens.refresh)
                        response.request.newBuilder().header("Authorization", "Bearer ${result.tokens.access}").build()
                    }
                    // Only a rejected refresh token signs the user out. A dropped connection or a
                    // server error keeps the session (and the offline caller ID directory) for later.
                    RefreshResult.Rejected -> {
                        sessions.clear()
                        null
                    }
                    RefreshResult.Unavailable -> null
                }
            }
        }
    }

    private sealed interface RefreshResult {
        data class Success(val tokens: TokenPair) : RefreshResult
        data object Rejected : RefreshResult
        data object Unavailable : RefreshResult
    }

    private fun refresh(refreshToken: String): RefreshResult = try {
        val body = ApiJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken)).toRequestBody(jsonType)
        val request = Request.Builder().url(BuildConfig.API_BASE_URL + "auth/tenant/refresh/").post(body).build()
        baseClient.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> RefreshResult.Success(ApiJson.decodeFromString(TokenPair.serializer(), response.body!!.string()))
                response.code == 400 || response.code == 401 -> RefreshResult.Rejected
                else -> RefreshResult.Unavailable
            }
        }
    } catch (e: IOException) {
        RefreshResult.Unavailable
    } catch (e: kotlinx.serialization.SerializationException) {
        RefreshResult.Unavailable
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    val api: BeamApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(
            baseClient.newBuilder()
                .addInterceptor(authInterceptor)
                .authenticator(tokenAuthenticator)
                .build(),
        )
        .addConverterFactory(ApiJson.asConverterFactory(jsonType))
        .build()
        .create(BeamApi::class.java)
}

/** An API failure with a message that can be shown to the user. */
class ApiException(message: String, val status: Int? = null, val code: String? = null) : Exception(message) {
    val isModuleDisabled: Boolean get() = status == 403 && message?.contains("not enabled", ignoreCase = true) == true
}

/** Runs [block], turning HTTP and network errors into [ApiException]s with readable messages. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw e.toApiException()
} catch (e: IOException) {
    throw ApiException("Can't reach Beam. Check your connection and try again.")
}

private fun HttpException.toApiException(): ApiException {
    val status = code()
    val raw = response()?.errorBody()?.string().orEmpty()
    val detail = runCatching { ApiJson.decodeFromString(DetailResponse.serializer(), raw) }.getOrNull()
    val fieldMessage = if (detail?.detail == null) firstFieldError(raw) else null
    val message = when {
        detail?.code == "tenant_read_only" -> "Your Beam account is read-only right now. Renew the subscription to make changes."
        detail?.code == "tenant_suspended" -> "Your Beam account is suspended. Renew the subscription on the web dashboard."
        status == 401 -> "Your session has ended. Sign in again."
        detail?.detail != null -> detail.detail
        fieldMessage != null -> fieldMessage
        status == 403 -> "You don't have permission to do that."
        status == 404 -> "That record no longer exists."
        status == 429 -> "Too many attempts. Wait a minute and try again."
        status >= 500 -> "Beam had a problem (error $status). Try again shortly."
        else -> "Request failed (error $status)."
    }
    return ApiException(message, status, detail?.code)
}

/** Picks the first message out of a DRF field-validation body like {"phone": ["…"]}. */
private fun firstFieldError(raw: String): String? = runCatching {
    val obj = ApiJson.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject ?: return null
    val (field, value) = obj.entries.firstOrNull() ?: return null
    val text = when (value) {
        is kotlinx.serialization.json.JsonArray -> (value.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)?.content
        is kotlinx.serialization.json.JsonPrimitive -> value.content
        else -> null
    } ?: return null
    if (field == "non_field_errors" || field == "detail") text
    else "${field.replace('_', ' ').replaceFirstChar { it.uppercase() }}: $text"
}.getOrNull()

/** Loads every page of a paginated list, stopping early when [stop] says the rest isn't needed. */
suspend fun <T> fetchAllPages(maxPages: Int = 50, stop: (List<T>) -> Boolean = { false }, page: suspend (Int) -> Page<T>): List<T> {
    val all = mutableListOf<T>()
    var number = 1
    while (number <= maxPages) {
        val result = page(number)
        all += result.results
        if (result.next == null || stop(result.results)) break
        number++
    }
    return all
}
