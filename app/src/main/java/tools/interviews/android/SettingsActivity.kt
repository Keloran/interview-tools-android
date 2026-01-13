package tools.interviews.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.launch
import tools.interviews.android.data.api.APIService
import tools.interviews.android.data.api.SyncService
import tools.interviews.android.util.FoldableOrientationManager
import java.time.format.DateTimeFormatter
import androidx.core.net.toUri
import android_app.R

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
    private lateinit var itemGithub: LinearLayout
    private lateinit var textVersion: TextView

    private lateinit var syncService: SyncService
    private lateinit var appUpdateManager: AppUpdateManager

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Snackbar.make(textVersion, "Update installed", Snackbar.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "Update flow failed with result code: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        syncService = (application as InterviewApplication).syncService
        appUpdateManager = AppUpdateManagerFactory.create(this)

        // Handle orientation based on fold state (candybar vs tablet mode)
        FoldableOrientationManager(this).attach(this)

        setupViews()
        setupToolbar()
        setupClickListeners()
        setupVersionDoubleTap()
        observeAuthState()
        observeSyncState()
        displayVersion()
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupVersionDoubleTap() {
        lifecycleScope.launch {
            val client = (application as InterviewApplication).flagsClient.await()

            if (client.isEnabled("version check")) {
                val gestureDetector =
                    GestureDetector(this@SettingsActivity, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            checkForUpdates()
                            return true
                        }
                    })

                textVersion.setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    true
                }
            }
        }
    }

    private fun checkForUpdates() {
        Snackbar.make(textVersion, "Checking for updates...", Snackbar.LENGTH_SHORT).show()

        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            when (info.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> {
                    if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            updateLauncher,
                            AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
                        )
                    } else {
                        Snackbar.make(textVersion, "Update available but not ready", Snackbar.LENGTH_SHORT).show()
                    }
                }
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                    Snackbar.make(textVersion, "You're on the latest version", Snackbar.LENGTH_SHORT).show()
                }
                else -> {
                    Snackbar.make(textVersion, "Unable to check for updates", Snackbar.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            Snackbar.make(textVersion, "Failed to check for updates", Snackbar.LENGTH_SHORT).show()
        }
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
        itemGithub = findViewById(R.id.itemGithub)
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
            val intent = Intent(Intent.ACTION_VIEW, "https://interviews.tools".toUri())
            startActivity(intent)
        }

        itemGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW,
                "https://github.com/keloran/interview-tools-android".toUri())
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
            textVersion.text = "Unknown"
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
