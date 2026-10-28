package tools.interviews.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview

@OptIn(ExperimentalCoroutinesApi::class)
class InterviewRepository(
    private val interviewDao: InterviewDao,
    private val companyDao: CompanyDao,
    private val gate: AccountOperationGate = AccountOperationGate(),
    private val owner: StateFlow<String> = MutableStateFlow(AccountOwners.GUEST),
    private val ownerNow: () -> String = { owner.value },
    private val transaction: suspend (suspend () -> Unit) -> Unit = { block -> block() }
) {
    private val scope = combine(gate.isBlocked, owner) { blocked, selected -> blocked to selected }
    val allInterviews: Flow<List<Interview>> = scope.flatMapLatest { (blocked, selected) -> flow {
        emit(emptyList()) // clear the old account before the new Room query emits
        if (!blocked) emitAll(interviewDao.getAllInterviews(selected).map { if (ownerNow() == selected) it else emptyList() })
    } }
    val allCompanies: Flow<List<String>> = scope.flatMapLatest { (blocked, selected) -> flow {
        emit(emptyList())
        if (!blocked) emitAll(companyDao.getAllCompanyNames(selected).map { if (ownerNow() == selected) it else emptyList() })
    } }
    val localImportCount = interviewDao.localImportCount()
    val legacyCount = interviewDao.legacyCount()
    val legacyLocalCount = interviewDao.legacyLocalCount()

    private fun checkOwner(expected: String) {
        if (ownerNow() != expected) throw CancellationException("Account changed. The old operation was cancelled.")
    }
    suspend fun insert(interview: Interview): Long = gate.run {
        checkOwner(interview.ownerId)
        interview.previousLocalId?.let {
            require(interviewDao.getInterviewById(it, interview.ownerId) != null) { "Previous stage belongs to another account or is missing." }
        }
        require(interview.id == 0L) { "Use update for an existing interview." }
        interview.companyId?.let {
            require(companyDao.getCompanyById(it, interview.ownerId) != null) { "Company belongs to another account." }
        }
        interviewDao.insert(interview)
    }
    suspend fun update(interview: Interview) = gate.run {
        checkOwner(interview.ownerId)
        if (interviewDao.getInterviewById(interview.id, interview.ownerId) == null) throw CancellationException("Interview is no longer in this account.")
        interview.companyId?.let {
            require(companyDao.getCompanyById(it, interview.ownerId) != null) { "Company belongs to another account." }
        }
        checkOwner(interview.ownerId)
        interviewDao.update(interview)
    }
    suspend fun delete(interview: Interview) = gate.run {
        checkOwner(interview.ownerId)
        if (interviewDao.getInterviewById(interview.id, interview.ownerId) == null) throw CancellationException("Interview is no longer in this account.")
        interviewDao.delete(interview)
    }
    suspend fun deleteById(id: Long) {
        val selected = ownerNow()
        gate.run { checkOwner(selected); interviewDao.deleteById(id, selected) }
    }
    suspend fun getById(id: Long): Interview? {
        val selected = ownerNow()
        if (gate.isBlocked.value) return null
        val record = interviewDao.getInterviewById(id, selected)
        checkOwner(selected)
        return record
    }
    suspend fun findOrCreateCompany(name: String, expectedOwner: String = ownerNow()): Company = gate.run {
        checkOwner(expectedOwner)
        findCompany(name, expectedOwner)
    }
    private suspend fun findCompany(name: String, selected: String): Company {
        companyDao.getCompanyByName(name, selected)?.let { return it }
        val company = Company(name = name, ownerId = selected)
        return company.copy(id = companyDao.insert(company))
    }
    suspend fun updateCompanyServerId(localId: Long, serverId: Int) {
        val selected = ownerNow()
        gate.run {
            checkOwner(selected)
            companyDao.getCompanyById(localId, selected)?.let { company -> companyDao.update(company.copy(serverId = serverId)) }
        }
    }
    suspend fun companyNames(): List<String> {
        val selected = ownerNow()
        if (gate.isBlocked.value) return emptyList()
        val names = companyDao.getAllCompaniesList(selected).map { it.name }
        checkOwner(selected)
        return names
    }

    suspend fun getCompanyByServerId(serverId: Int): Company? {
        val selected = ownerNow()
        if (gate.isBlocked.value) return null
        val company = companyDao.getCompanyByServerId(serverId, selected)
        checkOwner(selected)
        return company
    }

    /** Explicitly move only device-local rows; old downloaded account caches stay quarantined. */
    suspend fun importLocal(expectedOwner: String, restoreLegacyOnly: Boolean = false) = gate.run {
        checkOwner(expectedOwner)
        if (!restoreLegacyOnly) require(expectedOwner != AccountOwners.GUEST) { "Sign in before importing." }
        transaction {
            val sources = if (restoreLegacyOnly) listOf(AccountOwners.LEGACY) else listOf(AccountOwners.GUEST, AccountOwners.LEGACY)
            for (source in sources) for (record in interviewDao.localOnly(source)) {
                checkOwner(expectedOwner)
                val company = findCompany(record.companyName, expectedOwner)
                interviewDao.update(record.copy(ownerId = expectedOwner, companyId = company.id))
            }
            checkOwner(expectedOwner)
        }
    }
}
