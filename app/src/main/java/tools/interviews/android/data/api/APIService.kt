package tools.interviews.android.data.api

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong
import java.io.IOException
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface InterviewsAPI {
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

    @POST("interview")
    suspend fun createInterview(@Body request: CreateInterviewRequest): APIInterview

    @PUT("interview/{id}")
    suspend fun updateInterview(
        @Path("id") id: Int,
        @Body request: UpdateInterviewRequest
    ): APIInterview
}

class APIService private constructor() {
    companion object {
        private const val BASE_URL = "https://interviews.tools/api/"

        @Volatile
        private var instance: APIService? = null

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
                .baseUrl(BASE_URL)
                .client(client)
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

    suspend fun createInterview(request: CreateInterviewRequest): APIInterview {
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

    suspend fun updateInterview(id: Int, request: UpdateInterviewRequest): APIInterview {
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