package tools.interviews.android

import androidx.room.withTransaction
import android.app.Application
import android_app.BuildConfig
import java.io.File
import tools.interviews.android.data.account.AccountDataManager
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.data.account.HttpAccountDataApi
import tools.interviews.android.data.account.PreferencesDeletionCheckpoint
import tools.interviews.android.data.api.APIService
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.clerk.api.Clerk
import gg.flags.client.Auth
import gg.flags.client.FlagsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import tools.interviews.android.data.InterviewDatabase
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.data.api.SyncService

class InterviewApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val deletionCheckpoint by lazy { PreferencesDeletionCheckpoint(getSharedPreferences("account-deletion", MODE_PRIVATE)) }
    val accountGate by lazy { AccountOperationGate(deletionCheckpoint.read() != null) }
    val accountData by lazy {
        AccountDataManager(HttpAccountDataApi(), { Clerk.user?.id }, {
            Clerk.sessionFlow.value?.fetchToken()?.successOrNull()?.jwt ?: error("Please sign in again before retrying.")
        }, deletionCheckpoint, accountGate, { database.clearAccountCache(it) }, {
            // SDK 0.1.31 always clears local credentials, even if the already-deleted remote session returns an error.
            Clerk.signOut()
            check(Clerk.user?.id != it) { "Your account is deleted, but local sign-out needs to be retried." }
        },
            { APIService.getInstance().setAuthToken(null) }, File(cacheDir, "account-exports"))
    }

    val database by lazy { InterviewDatabase.getDatabase(this) }
    val repository by lazy { InterviewRepository(database.interviewDao(), database.companyDao(), accountGate) }
    val syncService by lazy {
        SyncService(
            interviewDao = database.interviewDao(),
            companyDao = database.companyDao(),
            gate = accountGate,
            currentAccount = { Clerk.user?.id },
            transaction = { block -> database.withTransaction { block() } }
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

        applicationScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            File(cacheDir, "account-exports").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }

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
