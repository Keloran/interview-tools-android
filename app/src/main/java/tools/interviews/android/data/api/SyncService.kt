package tools.interviews.android.data.api

import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.data.account.AccountOperationGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.interviews.android.data.CompanyDao
import tools.interviews.android.data.InterviewDao
import tools.interviews.android.model.Company
import tools.interviews.android.model.*
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.LocalDateTime
import com.google.gson.Gson

class SyncService(
    private val interviewDao: InterviewDao,
    private val companyDao: CompanyDao,
    private val apiService: APIService = APIService.getInstance(),
    private val writes: SyncWrites = apiService,
    private val gate: AccountOperationGate = AccountOperationGate(),
    private val currentAccount: () -> String? = { null },
    private val authenticate: suspend (String) -> Unit = {},
    private val remoteFactory: () -> SyncRemote = { apiService.syncRemote() },
    private val transaction: suspend (suspend () -> Unit) -> Unit = { block -> block() }
) {
    companion object {
        private const val TAG = "SyncService"
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncDate = MutableStateFlow<LocalDateTime?>(null)
    val lastSyncDate: StateFlow<LocalDateTime?> = _lastSyncDate.asStateFlow()

    private val _syncError = MutableStateFlow<Exception?>(null)
    val syncError: StateFlow<Exception?> = _syncError.asStateFlow()

    private val gson = Gson()

    fun resetSessionState() {
        _lastSyncDate.value = null
        _syncError.value = null
    }

    // MARK: - Sync All Data (Push then Pull)

    suspend fun syncAll() = gate.run {
        if (_isSyncing.value) {
            Log.d(TAG, "Sync already in progress, skipping")
            return@run
        }

        _isSyncing.value = true
        _syncError.value = null

        try {
            val account = currentAccount() ?: error("Please sign in before syncing.")
            authenticate(account)
            if (currentAccount() != account) throw CancellationException("Account changed before syncing.")
            pushLocalInterviews(account) { if (currentAccount() != account) throw CancellationException("Account changed during sync.") }
            val remote = remoteFactory()
            fun checkAccount() {
                remote.checkSession()
                if (currentAccount() != account) throw CancellationException("Account changed during sync.")
            }
            checkAccount()
            val catalogStages = remote.stages()
            checkAccount()
            val snapshot = InterviewSyncSnapshotLoader(remote).load(interviewDao.getAllServerIds(account).toSet())
            checkAccount()
            // No local reconciliation occurs until all pages and missing-ID lookups have succeeded.
            transaction {
                currentCoroutineContext().ensureActive()
                checkAccount()
                syncCompanies(snapshot.companies, account)
                pullRemoteInterviews(snapshot, account, catalogStages.map { it.id }.toSet())
                currentCoroutineContext().ensureActive()
                checkAccount()
            }
            _lastSyncDate.value = LocalDateTime.now()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _syncError.value = if (e is retrofit2.HttpException && e.code() == 401) APIError.Unauthorized else e
            Log.e(TAG, "Sync failed", e)
        } finally {
            _isSyncing.value = false
        }
    }

    // MARK: - Sync Companies

    private suspend fun syncCompanies(apiCompanies: List<APICompany>, ownerId: String) {
        Log.d(TAG, "Received ${apiCompanies.size} company(ies) from server")

        // Get all local server IDs for tracking deletions
        val localServerIds = companyDao.getAllServerIds(ownerId).toMutableSet()

        for (apiCompany in apiCompanies) {
            // Check if we already have this company locally (by serverId)
            val existing = companyDao.getCompanyByServerId(apiCompany.id, ownerId) ?: companyDao.getLegacyByServerId(apiCompany.id)

            if (existing != null) {
                // Update existing company, preserving the local id
                val updatedCompany = existing.copy(
                    name = apiCompany.name,
                    ownerId = ownerId,
                    serverId = apiCompany.id
                )
                companyDao.update(updatedCompany)
                Log.d(TAG, "Updated local company: ${updatedCompany.name}")
            } else {
                // Check if we have a local company with the same name but no serverId
                val localByName = companyDao.getCompanyByName(apiCompany.name, ownerId)
                if (localByName != null && localByName.serverId == null) {
                    // Merge: update local company with server ID
                    val mergedCompany = localByName.copy(serverId = apiCompany.id, ownerId = ownerId)
                    companyDao.update(mergedCompany)
                    Log.d(TAG, "Merged local company '${apiCompany.name}' with server ID ${apiCompany.id}")
                } else {
                    // Insert new company from server
                    val newCompany = Company(
                        ownerId = ownerId,
                        serverId = apiCompany.id,
                        name = apiCompany.name
                    )
                    companyDao.insert(newCompany)
                    Log.d(TAG, "Inserted new company from server: ${newCompany.name}")
                }
            }

            // Remove from tracking set (company still exists on server)
            localServerIds.remove(apiCompany.id)
        }

        // Delete local companies that no longer exist on server
        // (Only delete those that have a serverId - local-only companies are preserved)
        for (deletedServerId in localServerIds) {
            companyDao.deleteUnreferencedByServerId(deletedServerId, ownerId)
            Log.d(TAG, "Deleted company with serverId: $deletedServerId (removed from server)")
        }
    }

    // MARK: - Push Local Interviews

    private suspend fun pushLocalInterviews(ownerId: String, checkAccount: () -> Unit) {
        // Get all interviews that haven't been synced yet (serverId is null)
        val unsyncedInterviews = interviewDao.getUnsyncedInterviews(ownerId)

        if (unsyncedInterviews.isEmpty()) {
            Log.d(TAG, "No local interviews to push")
            return
        }

        Log.d(TAG, "Pushing ${unsyncedInterviews.size} local interview(s) to server")

        val attempted = mutableMapOf<Long, Result<Int>>()
        val failures = mutableListOf<Exception>()
        for (interview in unsyncedInterviews) {
            try {
                // Push to server
                checkAccount()
                val serverId = pushInterviewUnsafe(interview, attempted = attempted)
                checkAccount()

                // Update local interview with server ID
                // pushInterviewUnsafe already saved the full acknowledged identity.
                checkAccount()

                Log.d(TAG, "Pushed interview '${interview.jobTitle}' -> server ID: $serverId")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push interview '${interview.jobTitle}': ${e.message}")
                failures += e
                // Continue independent drafts, but never retry a failed parent in this batch.
            }
        }
        if (failures.isNotEmpty()) throw java.io.IOException("${failures.size} interview upload(s) need retry. Local drafts were kept.", failures.first())
    }

    // MARK: - Pull Remote Interviews

    private suspend fun pullRemoteInterviews(snapshot: InterviewSyncSnapshot, ownerId: String, knownStageIds: Set<Int>) {
        val apiInterviews = snapshot.interviews
        Log.d(TAG, "Received ${apiInterviews.size} interview(s) from server")

        // Get all local server IDs for tracking deletions

        for (apiInterview in apiInterviews) {
            // Check if we already have this interview locally (by serverId)
            val existing = interviewDao.getInterviewByServerId(apiInterview.id, ownerId) ?: interviewDao.getLegacyByServerId(apiInterview.id)

            // Find or create the company for this interview
            val company = findOrCreateCompany(apiInterview.company, ownerId)

            if (existing != null) {
                // Update existing interview, preserving the local id
                val updatedInterview = mapApiInterviewToLocal(
                    apiInterview,
                    existingLocalId = existing.id,
                    companyId = company.id,
                    ownerId = ownerId,
                    knownStageIds = knownStageIds
                )
                interviewDao.update(updatedInterview)
                Log.d(TAG, "Updated local interview: ${updatedInterview.jobTitle}")
            } else {
                // Insert new interview from server
                val newInterview = mapApiInterviewToLocal(
                    apiInterview,
                    companyId = company.id,
                    ownerId = ownerId,
                    knownStageIds = knownStageIds
                )
                interviewDao.insert(newInterview)
                Log.d(TAG, "Inserted new interview from server: ${newInterview.jobTitle}")
            }

            // Remove from tracking set (interview still exists on server)
        }

        // Delete local interviews that no longer exist on server
        // (Only delete those that have a serverId - local-only interviews are preserved)
        for (deletedServerId in snapshot.deletedIds) {
            interviewDao.deleteByServerId(deletedServerId, ownerId)
            Log.d(TAG, "Deleted interview with serverId: $deletedServerId (removed from server)")
        }
    }

    // MARK: - Find or Create Company

    private suspend fun findOrCreateCompany(apiCompany: APICompany, ownerId: String): Company {
        // First try to find by server ID
        companyDao.getCompanyByServerId(apiCompany.id, ownerId)?.let { return it }

        // Then try to find by name and update with server ID
        companyDao.getCompanyByName(apiCompany.name, ownerId)?.let { existing ->
            if (existing.serverId == null) {
                val updated = existing.copy(serverId = apiCompany.id)
                companyDao.update(updated)
                return updated
            }
            return existing
        }

        // Create new company
        val newCompany = Company(
            ownerId = ownerId,
            serverId = apiCompany.id,
            name = apiCompany.name
        )
        val newId = companyDao.insert(newCompany)
        return newCompany.copy(id = newId)
    }

    // MARK: - Push Single Interview to API

    suspend fun pushInterview(interview: Interview): Int = gate.run {
        authenticate(interview.ownerId)
        pushInterviewUnsafe(interview)
    }

    private suspend fun pushInterviewUnsafe(interview: Interview, ancestors: Set<Long> = emptySet(), attempted: MutableMap<Long, Result<Int>> = mutableMapOf()): Int {
        attempted[interview.id]?.let { return it.getOrThrow() }
        return try {
            uploadWithParent(interview, ancestors, attempted).also { attempted[interview.id] = Result.success(it) }
        } catch (e: Exception) {
            attempted[interview.id] = Result.failure(e)
            throw e
        }
    }

    private suspend fun uploadWithParent(interview: Interview, ancestors: Set<Long>, attempted: MutableMap<Long, Result<Int>>): Int {
        if (interview.ownerId != currentAccount()) throw CancellationException("Interview belongs to another account.")
        require(interview.id !in ancestors) { "Previous-stage chain contains a cycle." }
        val current = interviewDao.getInterviewById(interview.id, interview.ownerId) ?: error("Interview no longer exists.")
        // A parent can already have been uploaded by an earlier recursive dependency.
        current.serverId?.let { return it }
        val parent = current.previousLocalId?.let { localId ->
            interviewDao.getInterviewById(localId, current.ownerId) ?: error("Previous stage is missing from this account.")
        }
        val previousServerId = parent?.let {
            it.serverId ?: pushInterviewUnsafe(it, ancestors + current.id, attempted)
        } ?: current.parentId
        if (current.ownerId != currentAccount()) throw CancellationException("Account changed before stage upload.")
        val request = InterviewRequests.create(current.copy(jobListing = current.jobListing ?: extractJobListing(current.metadataJSON)), previousServerId)
        val created = writes.createInterview(request)
        if (current.ownerId != currentAccount()) throw CancellationException("Account changed during stage upload.")
        // Persist the acknowledged identity before any subsequent operation can fail.
        interviewDao.update(current.copy(serverId = created.id, applicationId = created.applicationId,
            parentId = created.parentId, serverStageId = created.stage?.id,
            serverMethodId = created.stageMethod?.id, serverMethodName = created.stageMethod?.method,
            stageSelectionChanged = false, methodSelectionChanged = false))
        return created.id
    }

    suspend fun updateRemoteInterview(id: Int, interview: Interview): APIInterview = gate.run {
        authenticate(interview.ownerId)
        if (interview.ownerId != currentAccount()) throw CancellationException("Interview belongs to another account.")
        val request = InterviewRequests.update(interview)

        writes.updateInterview(id, request)
    }

    // MARK: - Mapping Helpers

    private fun mapApiInterviewToLocal(
        apiInterview: APIInterview,
        existingLocalId: Long? = null,
        companyId: Long? = null,
        ownerId: String = AccountOwners.GUEST,
        knownStageIds: Set<Int> = emptySet()
    ): Interview {
        // Parse application date
        val applicationDate = requireNotNull(InterviewTimes.parseDay(apiInterview.applicationDate)) { "Missing application date" }

        // Parse interview date
        val stage = mapApiStageToLocal(apiInterview.stage?.stage)
        val hasMeeting = stage !in setOf(InterviewStage.APPLIED, InterviewStage.OFFER, InterviewStage.TECHNICAL_TEST)
        val interviewInstant = if (hasMeeting) InterviewTimes.parseInstant(apiInterview.date) else null

        // Parse deadline
        val deadlineInstant = if (stage == InterviewStage.TECHNICAL_TEST) InterviewTimes.parseInstant(apiInterview.deadline) else null
        val offerRespondBy = if (stage == InterviewStage.OFFER) InterviewTimes.parseDay(apiInterview.deadline) else null

        // Map stage
        // Map method
        val method = mapApiMethodToLocal(apiInterview.stageMethod?.method)

        // Map outcome
        val outcome = mapApiOutcomeToLocal(apiInterview.outcome)

        // Build metadata JSON
        val metadataMap = mutableMapOf<String, Any>()
        apiInterview.metadata?.jobListing?.let { metadataMap["jobListing"] = it }
        apiInterview.metadata?.location?.let { metadataMap["location"] = it }
        metadataMap["applied"] = (apiInterview.stage?.stage == "Applied")
        val metadataJSON = gson.toJson(metadataMap)

        return Interview(
            id = existingLocalId ?: 0, // Use existing local ID or let Room auto-generate
            ownerId = ownerId,
            applicationId = apiInterview.applicationId,
            parentId = apiInterview.parentId,
            durationMinutes = apiInterview.durationMinutes,
            serverStageId = apiInterview.stage?.id,
            serverMethodId = apiInterview.stageMethod?.id,
            serverMethodName = apiInterview.stageMethod?.method,
            stageRecognized = apiInterview.stage?.id in knownStageIds,
            serverStageName = apiInterview.stage?.stage,
            serverOutcomeName = apiInterview.outcome,
            serverId = apiInterview.id, // Always set the server ID
            companyId = companyId, // Link to Company table
            jobTitle = apiInterview.jobTitle,
            companyName = apiInterview.company.name, // Keep for display
            clientCompany = apiInterview.clientCompany,
            stage = stage,
            method = method,
            outcome = outcome,
            applicationDate = applicationDate,
            interviewInstant = interviewInstant,
            deadlineInstant = deadlineInstant,
            offerRespondBy = offerRespondBy,
            interviewer = apiInterview.interviewer,
            link = apiInterview.link,
            jobListing = apiInterview.metadata?.jobListing,
            notes = apiInterview.notes,
            metadataJSON = metadataJSON
        )
    }

    // Made internal for testing
    internal fun mapApiStageToLocal(stageName: String?): InterviewStage {
        if (stageName == null) return InterviewStage.UNKNOWN

        return when (stageName.trim().lowercase()) {
            "applied" -> InterviewStage.APPLIED
            "phone screen" -> InterviewStage.PHONE_SCREEN
            "first stage" -> InterviewStage.FIRST_STAGE
            "second stage" -> InterviewStage.SECOND_STAGE
            "third stage" -> InterviewStage.THIRD_STAGE
            "fourth stage" -> InterviewStage.FOURTH_STAGE
            "technical test" -> InterviewStage.TECHNICAL_TEST
            "technical interview" -> InterviewStage.TECHNICAL_INTERVIEW
            "final stage" -> InterviewStage.FINAL_STAGE
            "onsite" -> InterviewStage.ONSITE
            "offer" -> InterviewStage.OFFER
            else -> InterviewStage.UNKNOWN
        }
    }

    // Made internal for testing
    internal fun mapApiMethodToLocal(methodName: String?): InterviewMethod? = InterviewCatalog.methodKind(methodName)

    // Made internal for testing
    internal fun mapApiOutcomeToLocal(outcomeName: String?): InterviewOutcome {
        if (outcomeName == null) return InterviewOutcome.UNKNOWN

        return when (outcomeName.trim().lowercase()) {
            "scheduled" -> InterviewOutcome.SCHEDULED
            "awaiting response", "awaiting_response", "pending" -> InterviewOutcome.AWAITING_RESPONSE
            "passed" -> InterviewOutcome.PASSED
            "rejected" -> InterviewOutcome.REJECTED
            "offer received", "offer_received" -> InterviewOutcome.OFFER_RECEIVED
            "offer accepted", "offer_accepted" -> InterviewOutcome.OFFER_ACCEPTED
            "offer declined", "offer_declined" -> InterviewOutcome.OFFER_DECLINED
            "withdrew", "withdrawn" -> InterviewOutcome.WITHDREW
            "no_response", "no response", "ghosted" -> InterviewOutcome.NO_RESPONSE
            else -> InterviewOutcome.UNKNOWN
        }
    }

    private fun extractJobListing(metadataJSON: String?): String? {
        if (metadataJSON == null) return null

        return try {
            val map = gson.fromJson(metadataJSON, Map::class.java)
            map["jobListing"] as? String
        } catch (e: Exception) {
            null
        }
    }
}