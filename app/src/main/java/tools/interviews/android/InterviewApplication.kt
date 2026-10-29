package tools.interviews.android

import androidx.room.withTransaction
import android.app.Application
import tools.interviews.android.BuildConfig
import kotlinx.coroutines.CancellationException
import tools.interviews.android.data.account.AccountSessionController
import tools.interviews.android.data.account.AccountOwners
import java.io.File
import tools.interviews.android.data.account.AccountDataManager
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.data.account.HttpAccountDataApi
import tools.interviews.android.data.account.PreferencesDeletionCheckpoint
import tools.interviews.android.data.api.APIService
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.clerk.api.session.GetTokenOptions
import com.clerk.api.Clerk
import gg.flags.client.Auth
import gg.flags.client.FlagsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tools.interviews.android.data.InterviewDatabase
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.data.api.SyncService

class InterviewApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val sessionController by lazy { AccountSessionController(accountGate,
        { APIService.getInstance().setAuthToken(null) }, { syncService.resetSessionState() }, { accountData.pendingCleanup }) }
    val owner get() = sessionController.owner
    fun ownerNow() = sessionController.effectiveOwner(Clerk.user?.id)
    suspend fun signOutAccount() = sessionController.signOut({ Clerk.user?.id }) { Clerk.auth.signOut() }

    fun schedulePendingSync(expectedOwner: String = ownerNow()) {
        if (expectedOwner == AccountOwners.GUEST) return
        applicationScope.launch {
            if (ownerNow() != expectedOwner) return@launch
            try { syncService.pushPending() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The durable queue and sync state retain the failure. */ }
        }
    }

    suspend fun authenticateAccount(expectedOwner: String): String {
        if (Clerk.user?.id != expectedOwner || ownerNow() != expectedOwner) throw CancellationException("Account changed before authentication.")
        APIService.getInstance().setAuthToken(null)
        val session = Clerk.sessionFlow.value ?: error("Please sign in again.")
        val jwt = session.fetchToken(GetTokenOptions(skipCache = true)).successOrNull()?.jwt ?: error("Couldn't refresh sign-in. Check your connection and try again.")
        if (Clerk.user?.id != expectedOwner || ownerNow() != expectedOwner) throw CancellationException("Account changed during authentication.")
        APIService.getInstance().setAuthToken(jwt)
        return jwt
    }

    private val deletionCheckpoint by lazy { PreferencesDeletionCheckpoint(getSharedPreferences("account-deletion", MODE_PRIVATE)) }
    val accountGate by lazy { AccountOperationGate(deletionCheckpoint.read() != null) }
    val accountData by lazy {
        AccountDataManager(HttpAccountDataApi(), { Clerk.user?.id }, {
            authenticateAccount(Clerk.user?.id ?: error("Please sign in again before retrying."))
        }, deletionCheckpoint, accountGate, { accountId, guests -> database.clearAccountCache(accountId, guests) }, {
            // Clerk clears local credentials, even if the already-deleted remote session returns an error.
            Clerk.auth.signOut()
            check(Clerk.user?.id != it) { "Your account is deleted, but local sign-out needs to be retried." }
        },
            { APIService.getInstance().setAuthToken(null) }, File(cacheDir, "account-exports"))
    }

    val followUpQueue: tools.interviews.android.data.api.FollowUpQueue by lazy { tools.interviews.android.data.api.FollowUpQueue(database.toolDraftDao(),accountGate,
        APIService.getInstance(),::ownerNow,{ authenticateAccount(it) },{ block->database.withTransaction { block() } }) }
    fun scheduleFollowUpSync(expectedOwner:String=ownerNow()) {
        if(expectedOwner==AccountOwners.GUEST) return
        applicationScope.launch { if(ownerNow()==expectedOwner) try { followUpQueue.push(expectedOwner) } catch(cancelled:CancellationException) { throw cancelled } catch(_:Exception) {} }
    }
    fun scheduleFullSync() { applicationScope.launch { try { syncService.syncAll() } catch(cancelled:CancellationException) { throw cancelled } catch(_:Exception) {} } }
    val storySaver by lazy { tools.interviews.android.data.api.StorySaveService(database.toolDraftDao(),accountGate,
        APIService.getInstance(),::ownerNow,{ authenticateAccount(it) }) }
    fun refreshDebriefReminders() {
        applicationScope.launch {
            accountGate.run {
                val expected=ownerNow()
                val rows=database.interviewDao().getAllInterviews(expected).first()
                if(ownerNow()==expected) {
                    tools.interviews.android.reminders.DebriefReminders.schedule(this@InterviewApplication,rows)
                    tools.interviews.android.reminders.FollowUpReminders.schedule(this@InterviewApplication,expected)
                }
            }
        }
    }
    val toolDrafts by lazy { tools.interviews.android.data.ToolDraftStore(database.toolDraftDao(), accountGate, ::ownerNow) }
    val reflectionStore by lazy { tools.interviews.android.data.api.ReflectionStore(database.interviewDao(), accountGate,
        APIService.getInstance(), ::ownerNow, { authenticateAccount(it) }) }
    val authenticatedRequests by lazy {
        tools.interviews.android.data.account.AuthenticatedRequests(accountGate, ::ownerNow, { authenticateAccount(it) })
    }
    val applicationHistory by lazy {
        tools.interviews.android.data.api.ApplicationHistory(database.applicationTimelineDao(), APIService.getInstance(),
            accountGate, ::ownerNow, { authenticateAccount(it) })
    }
    val database by lazy { InterviewDatabase.getDatabase(this) }
    val repository by lazy { InterviewRepository(database.interviewDao(), database.companyDao(), accountGate,
        owner, ::ownerNow, { block -> database.withTransaction { block() } }) }
    val syncService: SyncService by lazy {
        SyncService(
            interviewDao = database.interviewDao(),
            companyDao = database.companyDao(),
            gate = accountGate,
            currentAccount = { Clerk.user?.id },
            authenticate = { authenticateAccount(it) },
            transaction = { block -> database.withTransaction { block() } },
            additionalWrites = { owner -> check(followUpQueue.pushLocked(owner)==0) { "Some follow-ups need retry. Your records are saved on this device." } }
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

        applicationScope.launch {
            Clerk.userFlow.collect { user ->
                sessionController.changed(Clerk.user?.id)
            }
        }

        applicationScope.launch {
            owner.drop(1).collect {
                tools.interviews.android.reminders.DebriefReminders.cancel(this@InterviewApplication)
                tools.interviews.android.reminders.FollowUpReminders.cancel(this@InterviewApplication)
            }
        }
        applicationScope.launch {
            repository.allInterviews.collect { rows -> tools.interviews.android.reminders.DebriefReminders.schedule(this@InterviewApplication,rows) }
        }

        applicationScope.launch {
            combine(owner,accountGate.isBlocked,Clerk.isInitialized) { selected,blocked,ready -> if(!blocked && ready) selected else AccountOwners.GUEST }
                .distinctUntilChanged().collect { selected ->
                    if(selected!=AccountOwners.GUEST) {
                        scheduleFollowUpSync(selected)
                        tools.interviews.android.reminders.FollowUpReminders.schedule(this@InterviewApplication,selected)
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            val exports = File(cacheDir, "account-exports")
            // Old unpartitioned exports are disposable cache; never expose them to a new account.
            exports.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            exports.walkBottomUp().filter { it.isFile && it.lastModified() < cutoff }.forEach { it.delete() }
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
