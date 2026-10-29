package tools.interviews.android.data.api

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong
import java.io.IOException
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.google.gson.JsonObject
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface InterviewsAPI {
    @GET("mobile-capabilities")
    suspend fun mobileCapabilities(): JsonObject

    @GET("applications/{id}/timeline")
    suspend fun applicationTimeline(@Path("id") id: Int): JsonObject
    @PUT("applications/{id}/first-response")
    suspend fun saveFirstReply(@Path("id") id: Int, @Body body: okhttp3.RequestBody): JsonObject

    @GET("interview/{id}/prep")
    suspend fun prep(@Path("id") id: Int): JsonObject
    @PUT("interview/{id}/prep")
    suspend fun updatePrep(@Path("id") id: Int, @Body body: okhttp3.RequestBody): JsonObject
    @GET("questions") suspend fun questions(): com.google.gson.JsonArray
    @GET("stories") suspend fun stories(): com.google.gson.JsonArray
    @POST("native/stories") suspend fun createStory(@Body body: okhttp3.RequestBody): JsonObject
    @PUT("stories/{id}") suspend fun updateStory(@Path("id") id: Int, @Body body: okhttp3.RequestBody): JsonObject

    @GET("recruiters")
    suspend fun recruiters(): com.google.gson.JsonArray
    @PUT("applications/{id}/recruiter")
    suspend fun updateRecruiter(@Path("id") id: Int, @Body body: okhttp3.RequestBody): JsonObject

    @POST("check")
    suspend fun checkJob(@Body body: okhttp3.RequestBody): JsonObject

    // Companies
    @GET("companies")
    suspend fun fetchCompanies(): List<APICompany>

    // Stages
    @GET("stages")
    suspend fun fetchStages(): List<APIStage>

    // Stage Methods
    @GET("stage-methods")
    suspend fun fetchStageMethods(): List<APIStageMethod>

    // Interviews
    @GET("interviews")
    suspend fun fetchInterviews(
        @Query("date") date: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("includePast") includePast: Boolean? = null,
        @Query("companyId") companyId: Int? = null,
        @Query("company") companyName: String? = null,
        @Query("outcome") outcome: String? = null,
        @Query("take") take: Int? = null,
        @Query("skip") skip: Int? = null
    ): List<APIInterview>

    @GET("interview/{id}")
    suspend fun fetchInterview(@Path("id") id: Int): retrofit2.Response<APIInterview>

    @POST("native/interview")
    suspend fun createInterview(@Body request: CreateInterviewRequest): APIInterview

    @PUT("native/interview/{id}")
    suspend fun patchInterview(@Path("id") id: Int, @Body patch: okhttp3.RequestBody): APIInterview

    @PUT("interview/{id}")
    suspend fun updateInterview(
        @Path("id") id: Int,
        @Body request: UpdateInterviewRequest
    ): APIInterview
}

class APIService private constructor(private val baseUrl: String = BASE_URL, private val calls: okhttp3.Call.Factory? = null) : SyncWrites, ApplicationTimelineRemote, ReflectionRemote, StoryWrites {
    companion object {
        private const val BASE_URL = "https://interviews.tools/api/"

        @Volatile
        private var instance: APIService? = null

        internal fun forTesting(baseUrl: String, calls: okhttp3.Call.Factory) = APIService(baseUrl, calls)

        fun getInstance(): APIService {
            return instance ?: synchronized(this) {
                instance ?: APIService().also { instance = it }
            }
        }
    }

    private val authRevision = AtomicLong()
    @Volatile private var authToken: String? = null
    private var retrofit: Retrofit? = null
    private var api: InterviewsAPI? = null

    private val gson = Gson()

    @Synchronized
    fun setAuthToken(token: String?) {
        if (authToken == token) return
        authRevision.incrementAndGet()
        authToken = token
        // Rebuild retrofit when token changes
        retrofit = null
        api = null
    }

    @Synchronized
    private fun getApi(): InterviewsAPI {
        if (api == null) {
            val loggingInterceptor = NetworkLogging.interceptor()

            val requestToken = authToken
            val authInterceptor = Interceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")

                requestToken?.let {
                    requestBuilder.addHeader("Authorization", "Bearer $it")
                }

                chain.proceed(requestBuilder.build())
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .callFactory(calls ?: client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            api = retrofit!!.create(InterviewsAPI::class.java)
        }
        return api!!
    }

    @Synchronized
    fun syncRemote(): SyncRemote {
        check(!authToken.isNullOrBlank()) { "Please sign in before syncing." }
        val revision = authRevision.get()
        val client = getApi() // this client's auth interceptor captures one immutable token
        return object : SyncRemote {
            override fun checkSession() {
                if (authRevision.get() != revision) throw CancellationException("Session changed during sync. Existing history was kept.")
            }
            override suspend fun stages() = client.fetchStages()
            override suspend fun companies() = client.fetchCompanies()
            override suspend fun interviews(take: Int, skip: Int) = client.fetchInterviews(includePast = true, take = take, skip = skip)
            override suspend fun interview(id: Int): APIInterview? {
                val response = client.fetchInterview(id)
                return when {
                    response.code() == 404 -> null
                    response.isSuccessful -> response.body() ?: throw IOException("The sync record was empty.")
                    else -> throw retrofit2.HttpException(response)
                }
            }
        }
    }

    override suspend fun reflectionInterview(id: Int): APIInterview {
        val response=getApi().fetchInterview(id)
        if (!response.isSuccessful) throw handleHttpException(retrofit2.HttpException(response))
        return response.body() ?: error("Interview response was empty.")
    }
    override suspend fun applicationIdForInterview(id: Int): Int? {
        val response = getApi().fetchInterview(id)
        if (!response.isSuccessful) throw handleHttpException(retrofit2.HttpException(response))
        return response.body()?.applicationId
    }
    override suspend fun prep(id: Int) = getApi().prep(id)
    override suspend fun updatePrep(id: Int, body: JsonObject) {
        val response = getApi().updatePrep(id, body.toString().toRequestBody("application/json".toMediaType()))
        check(response.has("notes") && response.has("storyIds")) { "Preparation was not confirmed. Your draft is still saved." }
    }
    suspend fun questions() = getApi().questions()
    suspend fun stories() = getApi().stories()
    override suspend fun createStory(body: JsonObject) = saveStory(null,body)
    override suspend fun updateStory(id: Int, body: JsonObject) {
        val response=saveStory(id,body)
        check(response.get("ok")?.asBoolean==true) { "Story edit was not confirmed. Your draft is still saved." }
    }
    private suspend fun saveStory(id: Int?, body: JsonObject): JsonObject {
        val request = body.toString().toRequestBody("application/json".toMediaType())
        return if (id == null) getApi().createStory(request) else getApi().updateStory(id, request)
    }
    suspend fun recruiters() = getApi().recruiters()
    override suspend fun updateRecruiter(id: Int, body: JsonObject) {
        val response = getApi().updateRecruiter(id, body.toString().toRequestBody("application/json".toMediaType()))
        check(response.get("saved")?.asBoolean == true) { "Recruiter details were not confirmed. Try syncing again." }
    }

    suspend fun checkJob(body: JsonObject) = getApi().checkJob(body.toString().toRequestBody("application/json".toMediaType()))

    override suspend fun timeline(id: Int) = getApi().applicationTimeline(id)
    override suspend fun firstReply(id: Int, body: JsonObject) = getApi().saveFirstReply(id,
        body.toString().toRequestBody("application/json".toMediaType()))

    // MARK: - Companies

    suspend fun fetchCompanies(): List<APICompany> {
        return try {
            getApi().fetchCompanies()
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw APIError.NetworkError(e)
        }
    }

    // MARK: - Stages

    suspend fun fetchStages(): List<APIStage> {
        return try {
            getApi().fetchStages()
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw APIError.NetworkError(e)
        }
    }

    // MARK: - Stage Methods

    suspend fun fetchStageMethods(): List<APIStageMethod> {
        return try {
            getApi().fetchStageMethods()
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw APIError.NetworkError(e)
        }
    }

    // MARK: - Interviews

    suspend fun fetchInterviews(
        date: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        includePast: Boolean? = null,
        companyId: Int? = null,
        companyName: String? = null,
        outcome: String? = null
    ): List<APIInterview> {
        return try {
            getApi().fetchInterviews(
                date = date,
                dateFrom = dateFrom,
                dateTo = dateTo,
                includePast = includePast,
                companyId = companyId,
                companyName = companyName,
                outcome = outcome
            )
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw APIError.NetworkError(e)
        }
    }

    override suspend fun createInterview(request: CreateInterviewRequest): APIInterview {
        return try {
            getApi().createInterview(request)
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw APIError.NetworkError(e)
        }
    }

    override suspend fun updateInterview(id: Int, request: UpdateInterviewRequest): APIInterview {
        return try {
            getApi().updateInterview(id, request)
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw APIError.NetworkError(e)
        }
    }

    override suspend fun verifyCreateSupport() {
        try {
            check(getApi().mobileCapabilities().get("interviewCreateIdempotency")?.asInt == 1) {
                "Uploads are temporarily unavailable. Your changes are saved on this device."
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw APIError.ServerError("Uploads are temporarily unavailable. Your changes are saved on this device. Try again later.") }
    }

    override suspend fun patchInterview(id: Int, patch: JsonObject): APIInterview {
        try {
            // Serialize the JSON tree directly: omitted and explicit-null fields have different meanings.
            return getApi().patchInterview(id, patch.toString().toRequestBody("application/json".toMediaType()))
        } catch (e: retrofit2.HttpException) { throw handleHttpException(e) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { throw APIError.NetworkError(e) }
    }

    // MARK: - Private Helpers

    private fun handleHttpException(e: retrofit2.HttpException): APIError {
        return when (e.code()) {
            401 -> APIError.Unauthorized
            else -> {
                val errorBody = e.response()?.errorBody()?.string()
                val message = try {
                    errorBody?.let {
                        gson.fromJson(it, ErrorResponse::class.java)?.message
                    } ?: "HTTP ${e.code()}"
                } catch (_: Exception) {
                    "HTTP ${e.code()}"
                }
                APIError.ServerError(message)
            }
        }
    }
}