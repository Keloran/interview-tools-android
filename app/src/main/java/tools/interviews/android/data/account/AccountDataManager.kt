package tools.interviews.android.data.account

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DeletionCheckpoint {
    fun read(): PendingDeletion?
    fun save(value: PendingDeletion)
    fun clear()
}
data class PendingDeletion(val accountId: String, val clearGuestData: Boolean)

/** Remote acknowledgement is checkpointed so local cleanup/sign-out failures can be retried. */
class AccountDataManager(
    private val api: AccountDataApi,
    private val currentAccount: () -> String?,
    private val token: suspend () -> String,
    private val checkpoint: DeletionCheckpoint,
    private val gate: AccountOperationGate,
    private val clearLocal: suspend (String, Boolean) -> Unit,
    private val signOut: suspend (String) -> Unit,
    private val clearToken: () -> Unit,
    private val exportDirectory: File
) {
    private val operations = Mutex()
    private var confirmed: PendingDeletion? = null
    private fun pending() = checkpoint.read() ?: confirmed
    val pendingCleanup: Boolean get() = pending() != null

    suspend fun delete(confirmation: String, clearGuestData: Boolean, expectedAccountId: String? = currentAccount()) = withContext(NonCancellable) {
        operations.withLock {
            require(confirmation.trim().lowercase() == "delete my account") { "Type delete my account to confirm." }
            val pending = pending()
            val accountId = pending?.accountId ?: expectedAccountId ?: error("Please sign in first.")
            var remoteConfirmed = pending != null
            gate.closeAndRun {
                try {
                    if (!remoteConfirmed) {
                        val jwt = token()
                        check(currentAccount() == accountId) { "Account changed. Please retry from Settings." }
                        api.deleteAccount(jwt, confirmation)
                        remoteConfirmed = true
                        confirmed = PendingDeletion(accountId, clearGuestData)
                    }
                    checkpoint.save(pending ?: checkNotNull(confirmed))
                    // Use the confirmed choice after restart, not a changed checkbox in the retry UI.
                    clearLocal(accountId, pending?.clearGuestData ?: clearGuestData)
                    val accountExports = File(exportDirectory, AccountOwners.exportFolder(accountId))
                    if (accountExports.exists()) check(accountExports.deleteRecursively()) { "Could not remove cached exports. Retry cleanup." }
                    clearToken()
                    // Never sign a different account out if the Clerk session changed during the request.
                    if (currentAccount() == accountId) signOut(accountId)
                    checkpoint.clear()
                    confirmed = null
                    gate.reopen()
                } catch (error: Exception) {
                    if (!remoteConfirmed) gate.reopen()
                    throw error
                }
            }
        }
    }

    suspend fun export(kind: ExportKind): File = operations.withLock {
        gate.run {
            check(!pendingCleanup) { "Finish account cleanup before exporting." }
            val accountId = currentAccount() ?: error("Please sign in to export your data.")
            val jwt = token()
            check(currentAccount() == accountId) { "Account changed. Please retry." }
            val file = api.export(jwt, kind, File(exportDirectory, AccountOwners.exportFolder(accountId)))
            if (currentAccount() != accountId) {
                file.delete()
                error("Account changed. The export was discarded.")
            }
            file
        }
    }
}
