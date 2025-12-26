package tools.interviews.android

import android.app.Application
import com.clerk.api.Clerk
import gg.flags.client.Auth
import gg.flags.client.FlagsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import tools.interviews.android.data.InterviewDatabase
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.data.api.SyncService

class InterviewApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { InterviewDatabase.getDatabase(this) }
    val repository by lazy { InterviewRepository(database.interviewDao(), database.companyDao()) }
    val syncService by lazy {
        SyncService(
            interviewDao = database.interviewDao(),
            companyDao = database.companyDao()
        )
    }

    lateinit var flagsClient: Deferred<FlagsClient>
        private set

    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(
            context = this,
            publishableKey = "pk_live_Y2xlcmsuaW50ZXJ2aWV3cy50b29scyQ"
        )

        flagsClient = applicationScope.async {
            FlagsClient.builder().auth(
                Auth(
                    projectId = BuildConfig.FLAGS_PROJECT_ID,
                    agentId = BuildConfig.FLAGS_AGENT_ID,
                    environmentId = BuildConfig.FLAGS_ENVIRONMENT_ID
                )
            ).build()
        }
    }
}
