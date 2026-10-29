package tools.interviews.android.data.account

import java.security.MessageDigest

object AccountOwners {
    const val GUEST = "guest"
    const val LEGACY = "legacy"
    fun forUser(userId: String?) = userId ?: GUEST
    fun exportFolder(accountId: String) = MessageDigest.getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
