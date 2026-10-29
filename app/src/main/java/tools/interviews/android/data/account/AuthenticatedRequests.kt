package tools.interviews.android.data.account

import kotlinx.coroutines.CancellationException

/** One fresh credential and an account check around every authenticated request. */
class AuthenticatedRequests(
    private val gate: AccountOperationGate,
    private val ownerNow: () -> String,
    private val authenticate: suspend (String) -> Unit
) {
    suspend fun <T> run(owner: String, block: suspend () -> T): T = gate.run {
        check(owner != AccountOwners.GUEST) { "Sign in from Settings to use your account history." }
        fun checkOwner() { if (ownerNow() != owner) throw CancellationException("Account changed.") }
        checkOwner(); authenticate(owner); checkOwner()
        val result = block()
        checkOwner()
        result
    }
}
