package tools.interviews.android.data.api

import com.google.gson.annotations.SerializedName

// MARK: - API Response Models

data class APIInterview(
    val id: Int,
    val jobTitle: String,
    val interviewer: String?,
    val company: APICompany,
    val clientCompany: String?,
    val stage: APIStage?,
    val stageMethod: APIStageMethod?,
    val applicationDate: String,
    val date: String?,
    val deadline: String?,
    val outcome: String?,
    val notes: String?,
    val metadata: APIMetadata?,
    val link: String?
)

data class APICompany(
    val id: Int,
    val name: String
)

data class APIStage(
    val id: Int,
    val stage: String
)

data class APIStageMethod(
    val id: Int,
    val method: String
)

data class APIMetadata(
    val jobListing: String?,
    val location: String?
)

// MARK: - API Request Models

data class CreateInterviewRequest(
    val stage: String,
    val companyName: String,
    val clientCompany: String?,
    val jobTitle: String,
    val jobPostingLink: String?,
    val date: String?,
    val deadline: String?,
    val interviewer: String?,
    val locationType: String?,
    val interviewLink: String?,
    val notes: String?
)

data class UpdateInterviewRequest(
    val outcome: String?,
    val stage: String?,
    val date: String?,
    val deadline: String?,
    val interviewer: String?,
    val notes: String?,
    val link: String?
)

// MARK: - API Error

sealed class APIError : Exception() {
    object Unauthorized : APIError() {
        override val message: String = "Unauthorized. Please sign in."
    }

    object InvalidResponse : APIError() {
        override val message: String = "Invalid response from server"
    }

    data class NetworkError(val error: Throwable) : APIError() {
        override val message: String = "Network error: ${error.localizedMessage}"
    }

    data class ServerError(override val message: String) : APIError()

    data class DecodingError(val error: Throwable) : APIError() {
        override val message: String = "Failed to parse response: ${error.localizedMessage}"
    }
}

// Error response from server
data class ErrorResponse(
    val message: String?
)