package tools.interviews.android.data.api

import okhttp3.logging.HttpLoggingInterceptor
import tools.interviews.android.BuildConfig

object NetworkLogging {
    fun interceptor(debug: Boolean = BuildConfig.DEBUG, logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT) =
        HttpLoggingInterceptor(logger).apply {
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            // BASIC omits headers and bodies even in debug; release emits nothing.
            level = if (debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
}
