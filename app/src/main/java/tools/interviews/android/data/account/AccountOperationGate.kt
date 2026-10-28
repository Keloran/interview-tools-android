package tools.interviews.android.data.account

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Finishes active writes before deletion and rejects queued work from the old session. */
class AccountOperationGate(initiallyClosed: Boolean = false) {
    private val mutex = Mutex()
    private val blocked = MutableStateFlow(initiallyClosed)
    val isBlocked = blocked.asStateFlow()
    private val generation = AtomicLong()

    suspend fun <T> run(block: suspend () -> T): T {
        val started = generation.get()
        check(!blocked.value) { "Account cleanup is pending. Open Settings to finish it." }
        return mutex.withLock {
            check(!blocked.value && started == generation.get()) { "Account changed while this operation was waiting." }
            block()
        }
    }

    suspend fun <T> closeAndRun(block: suspend () -> T): T {
        blocked.value = true
        generation.incrementAndGet()
        return mutex.withLock { block() }
    }

    fun reopen() {
        generation.incrementAndGet()
        blocked.value = false
    }
}
