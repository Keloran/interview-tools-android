package tools.interviews.android.data.account

import android.content.SharedPreferences

class PreferencesDeletionCheckpoint(private val preferences: SharedPreferences) : DeletionCheckpoint {
    override fun read(): PendingDeletion? = preferences.getString("account", null)?.let {
        PendingDeletion(it, preferences.getBoolean("clearGuests", false))
    }
    override fun save(value: PendingDeletion) {
        check(preferences.edit().putString("account", value.accountId).putBoolean("clearGuests", value.clearGuestData).commit()) {
            "Your account was deleted, but local cleanup could not be saved. Retry cleanup before closing the app."
        }
    }
    override fun clear() {
        check(preferences.edit().clear().commit()) { "Local cleanup could not finish. Please retry." }
    }
}
