package tools.interviews.android.data

import kotlinx.coroutines.CancellationException
import tools.interviews.android.data.account.AccountOperationGate

class ToolDraftStore(
    private val dao: ToolDraftDao,
    private val gate: AccountOperationGate,
    private val ownerNow: () -> String,
) {
    private fun check(owner: String) {
        if (ownerNow() != owner) throw CancellationException("Account changed.")
    }

    suspend fun get(owner: String, key: String): String? = gate.run {
        check(owner)
        dao.get(owner, key)?.json.also { check(owner) }
    }

    suspend fun save(owner: String, key: String, json: String) = gate.run {
        check(owner)
        dao.put(ToolDraft(owner, key, json))
    }

    suspend fun delete(owner: String, key: String) = gate.run {
        check(owner)
        dao.delete(owner, key)
    }
}
