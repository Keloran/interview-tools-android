package tools.interviews.android.data.api

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
        @Query("outcome") outcome: String? = null
    ): List<APIInterview>

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

    private var authToken: String? = null
    private var retrofit: Retrofit? = null
    private var api: InterviewsAPI? = null

    private val gson = Gson()

    fun setAuthToken(token: String?) {
        authToken = token
        // Rebuild retrofit when token changes
        retrofit = null
        api = null
    }

    private fun getApi(): InterviewsAPI {
        if (api == null) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val authInterceptor = Interceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")

                authToken?.let {
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

    // MARK: - Companies

    suspend fun fetchCompanies(): List<APICompany> {
        return try {
            getApi().fetchCompanies()
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
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
        } catch (e: Exception) {
            throw APIError.NetworkError(e)
        }
    }

    suspend fun createInterview(request: CreateInterviewRequest): APIInterview {
        return try {
            getApi().createInterview(request)
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
        } catch (e: Exception) {
            throw APIError.NetworkError(e)
        }
    }

    suspend fun updateInterview(id: Int, request: UpdateInterviewRequest): APIInterview {
        return try {
            getApi().updateInterview(id, request)
        } catch (e: retrofit2.HttpException) {
            throw handleHttpException(e)
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