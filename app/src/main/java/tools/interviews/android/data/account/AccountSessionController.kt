package tools.interviews.android.data.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Separates visible data ownership from an SDK logout request that can still be in flight. */
class AccountSessionController(
    private val gate: AccountOperationGate,
    private val clearToken: () -> Unit,
    private val resetSync: () -> Unit,
    private val cleanupPending: () -> Boolean
) {
    private val selected = MutableStateFlow(AccountOwners.GUEST)
    val owner = selected.asStateFlow()
    @Volatile private var signingOut = false
    fun effectiveOwner(userId: String?) = if (signingOut) AccountOwners.GUEST else AccountOwners.forUser(userId)

    suspend fun changed(userId: String?) {
        val next = effectiveOwner(userId)
        if (next == selected.value) return
        clearToken()
        selected.value = next
        gate.closeAndRun {
            resetSync()
            if (!cleanupPending()) gate.reopen()
        }
    }

    suspend fun signOut(currentUser: () -> String?, sdkSignOut: suspend () -> Unit) {
        signingOut = true
        clearToken()
        selected.value = AccountOwners.GUEST
        gate.closeAndRun {
            try {
                sdkSignOut()
            } finally {
                signingOut = false
                selected.value = AccountOwners.forUser(currentUser())
                resetSync()
                if (currentUser() == null && !cleanupPending()) gate.reopen()
            }
        }
    }
}
