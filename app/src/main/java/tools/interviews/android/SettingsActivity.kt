package tools.interviews.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import tools.interviews.android.data.api.APIService
import tools.interviews.android.data.api.SyncService
import java.time.format.DateTimeFormatter

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var itemSignIn: LinearLayout
    private lateinit var buttonSignIn: MaterialButton
    private lateinit var textAccountTitle: TextView
    private lateinit var textAccountSubtitle: TextView
    private lateinit var sectionSync: LinearLayout
    private lateinit var itemSyncNow: LinearLayout
    private lateinit var textLastSynced: TextView
    private lateinit var itemWebsite: LinearLayout
    private lateinit var textVersion: TextView

    private lateinit var syncService: SyncService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        syncService = (application as InterviewApplication).syncService

        setupViews()
        setupToolbar()
        setupClickListeners()
        displayVersion()
        observeAuthState()
        observeSyncState()
    }

    override fun onResume() {
        super.onResume()
        updateAuthUI()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        itemSignIn = findViewById(R.id.itemSignIn)
        buttonSignIn = findViewById(R.id.buttonSignIn)
        textAccountTitle = findViewById(R.id.textAccountTitle)
        textAccountSubtitle = findViewById(R.id.textAccountSubtitle)
        sectionSync = findViewById(R.id.sectionSync)
        itemSyncNow = findViewById(R.id.itemSyncNow)
        textLastSynced = findViewById(R.id.textLastSynced)
        itemWebsite = findViewById(R.id.itemWebsite)
        textVersion = findViewById(R.id.textVersion)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        buttonSignIn.setOnClickListener {
            val user = Clerk.user
            if (user != null) {
                // User is signed in, sign them out
                lifecycleScope.launch {
                    Clerk.signOut()
                    updateAuthUI()
                }
            } else {
                // User is not signed in, launch sign in screen
                val intent = Intent(this, SignInActivity::class.java)
                startActivity(intent)
            }
        }

        itemSyncNow.setOnClickListener {
            performSync()
        }

        itemWebsite.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://interviews.tools"))
            startActivity(intent)
        }
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            Clerk.userFlow.collect { user ->
                updateAuthUI(user)
            }
        }
    }

    private fun updateAuthUI(user: com.clerk.api.user.User? = null) {
        val currentUser = user ?: Clerk.user
        if (currentUser != null) {
            // User is signed in
            val displayName = currentUser.primaryEmailAddress?.emailAddress
                ?: currentUser.username
                ?: "Signed In"
            textAccountTitle.text = displayName
            textAccountSubtitle.text = "Tap to sign out"
            buttonSignIn.text = "Sign Out"
            sectionSync.isVisible = true
        } else {
            // User is not signed in (guest mode) - hide sync section
            textAccountTitle.text = "Guest Mode"
            textAccountSubtitle.text = "Sign in to sync across devices"
            buttonSignIn.text = "Sign In"
            sectionSync.isVisible = false
        }
    }

    private fun displayVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            textVersion.text = packageInfo.versionName
        } catch (e: Exception) {
            textVersion.text = "1.0"
        }
    }

    private fun observeSyncState() {
        // Observe syncing state
        lifecycleScope.launch {
            syncService.isSyncing.collect { isSyncing ->
                if (isSyncing) {
                    textLastSynced.text = "Syncing..."
                    itemSyncNow.isEnabled = false
                } else {
                    itemSyncNow.isEnabled = Clerk.user != null
                }
            }
        }

        // Observe last sync date
        lifecycleScope.launch {
            syncService.lastSyncDate.collect { lastSync ->
                if (lastSync != null) {
                    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
                    textLastSynced.text = "Last synced: ${lastSync.format(formatter)}"
                } else if (!syncService.isSyncing.value) {
                    textLastSynced.text = "Never synced"
                }
            }
        }

        // Observe sync errors
        lifecycleScope.launch {
            syncService.syncError.collect { error ->
                if (error != null) {
                    textLastSynced.text = "Sync failed"
                    Snackbar.make(itemSyncNow, "Sync failed: ${error.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performSync() {
        val user = Clerk.user
        if (user == null) {
            Snackbar.make(itemSyncNow, "Please sign in to sync", Snackbar.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                textLastSynced.text = "Syncing..."

                // Get the session token from Clerk
                val session = Clerk.sessionFlow.value
                val token = session?.fetchToken()?.successOrNull()

                if (token != null) {
                    Log.d(TAG, "Got auth token, starting sync...")
                    APIService.getInstance().setAuthToken(token.jwt)
                    syncService.syncAll()

                    Snackbar.make(itemSyncNow, "Sync completed", Snackbar.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "No auth token available")
                    textLastSynced.text = "Sync failed"
                    Snackbar.make(itemSyncNow, "Unable to get auth token", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed: ${e.message}", e)
                textLastSynced.text = "Sync failed"
                Snackbar.make(itemSyncNow, "Sync failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
