package tools.interviews.android

import tools.interviews.android.data.account.AccountOwners
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.checkbox.MaterialCheckBox
import tools.interviews.android.data.account.ExportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import tools.interviews.android.R

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

    private val accountData get() = (application as InterviewApplication).accountData
    private var accountActionBusy = false
    private var localImportCount = 0
    private var legacyLocalCount = 0
    private lateinit var sectionAccountData: LinearLayout
    private lateinit var exportApplications: MaterialButton
    private lateinit var exportStories: MaterialButton
    private lateinit var deleteAccount: MaterialButton

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
        observeLocalData()
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
        sectionAccountData = findViewById(R.id.sectionAccountData)
        exportApplications = findViewById(R.id.exportApplications)
        exportStories = findViewById(R.id.exportStories)
        deleteAccount = findViewById(R.id.deleteAccount)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        findViewById<MaterialButton>(R.id.importLocalData).setOnClickListener { confirmLocalImport(false) }
        findViewById<MaterialButton>(R.id.restoreLegacyLocalData).setOnClickListener { confirmLocalImport(true) }
        exportApplications.setOnClickListener { exportData(ExportKind.APPLICATIONS) }
        exportStories.setOnClickListener { exportData(ExportKind.STORIES) }
        deleteAccount.setOnClickListener { confirmAccountDeletion() }
        findViewById<MaterialButton>(R.id.deletionWebsite).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://interviews.tools/deleteme".toUri()))
        }
        buttonSignIn.setOnClickListener {
            val user = Clerk.user
            if (user != null) {
                // User is signed in, sign them out
                runAccountAction {
                    (application as InterviewApplication).signOutAccount()
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
                updateAuthUI()
            }
        }
    }

    private fun updateAuthUI() {
        val currentUser = Clerk.user
        val pending = accountData.pendingCleanup
        findViewById<MaterialButton>(R.id.importLocalData).apply {
            isVisible = currentUser != null && localImportCount > 0
            isEnabled = !pending && !accountActionBusy
            text = getString(R.string.import_guest_data, localImportCount)
        }
        findViewById<MaterialButton>(R.id.restoreLegacyLocalData).apply {
            isVisible = currentUser == null && legacyLocalCount > 0
            isEnabled = !pending && !accountActionBusy
            text = getString(R.string.restore_legacy_local, legacyLocalCount)
        }
        sectionAccountData.isVisible = currentUser != null || pending
        findViewById<TextView>(R.id.accountActionStatus).isVisible = accountActionBusy
        exportApplications.isEnabled = currentUser != null && !pending && !accountActionBusy
        exportStories.isEnabled = currentUser != null && !pending && !accountActionBusy
        deleteAccount.isEnabled = !accountActionBusy
        deleteAccount.setText(if (pending) R.string.retry_account_cleanup else R.string.delete_account)
        buttonSignIn.isEnabled = !accountActionBusy && !pending
        itemSyncNow.isEnabled = currentUser != null && !pending && !accountActionBusy
        if (currentUser != null) {
            // User is signed in
            val displayName = currentUser.primaryEmailAddress?.emailAddress
                ?: currentUser.username
                ?: "Signed In"
            textAccountTitle.text = displayName
            textAccountSubtitle.text = "Tap to sign out"
            buttonSignIn.text = "Sign Out"
            sectionSync.isVisible = !pending
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
                    itemSyncNow.isEnabled = Clerk.user != null && !accountActionBusy && !accountData.pendingCleanup
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


    private fun observeLocalData() {
        val repository = (application as InterviewApplication).repository
        lifecycleScope.launch { repository.localImportCount.collect { localImportCount = it; updateAuthUI() } }
        lifecycleScope.launch { repository.legacyLocalCount.collect { legacyLocalCount = it; updateAuthUI() } }
        lifecycleScope.launch { repository.legacyCount.collect { findViewById<TextView>(R.id.legacyCacheNotice).isVisible = it > 0 } }
    }

    private fun confirmLocalImport(restore: Boolean) {
        val owner = AccountOwners.forUser(Clerk.user?.id)
        val display = Clerk.user?.primaryEmailAddress?.emailAddress ?: "this account"
        MaterialAlertDialogBuilder(this)
            .setMessage(if (restore) getString(R.string.restore_legacy_explanation) else getString(R.string.import_guest_explanation, display))
            .setNegativeButton(R.string.cancel_account_action, null)
            .setPositiveButton(if (restore) R.string.confirm_local_restore else R.string.confirm_local_import) { _, _ ->
                runAccountAction {
                    (application as InterviewApplication).repository.importLocal(owner, restore)
                    if (!restore) performSync()
                }
            }.show()
    }

    private fun confirmAccountDeletion() {
        if (accountActionBusy) return
        if (accountData.pendingCleanup) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.retry_account_cleanup)
                .setMessage(R.string.account_cleanup_explanation)
                .setNegativeButton(R.string.cancel_account_action, null)
                .setPositiveButton(R.string.retry_account_cleanup) { _, _ -> deleteAccountData("delete my account", false) }.show()
            return
        }
        val expectedAccount = Clerk.user?.id ?: return
        val content = layoutInflater.inflate(R.layout.dialog_delete_account, null)
        val confirmation = content.findViewById<TextInputEditText>(R.id.deleteConfirmation)
        val guests = content.findViewById<MaterialCheckBox>(R.id.clearGuestData)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.delete_account).setView(content)
            .setNegativeButton(R.string.cancel_account_action, null)
            .setPositiveButton(R.string.delete_account, null).create()
        dialog.setOnShowListener {
            val submit = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            submit.isEnabled = false
            confirmation.addTextChangedListener { submit.isEnabled = it.toString().trim().equals("delete my account", ignoreCase = true) }
            submit.setOnClickListener {
                val typed = confirmation.text.toString()
                val clearGuests = guests.isChecked
                dialog.dismiss()
                deleteAccountData(typed, clearGuests, expectedAccount)
            }
        }
        dialog.show()
    }

    private fun deleteAccountData(confirmation: String, clearGuests: Boolean, expectedAccount: String? = Clerk.user?.id) {
        runAccountAction {
            accountData.delete(confirmation, clearGuests, expectedAccount)
            currentCoroutineContext().ensureActive()
            MaterialAlertDialogBuilder(this).setMessage(R.string.account_deleted)
                .setCancelable(false).setPositiveButton(android.R.string.ok) { _, _ ->
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }.show()
        }
    }

    private fun exportData(kind: ExportKind) {
        runAccountAction {
            val file = accountData.export(kind)
            currentCoroutineContext().ensureActive()
            val uri = FileProvider.getUriForFile(this, "$packageName.exports", file)
            val share = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            share.clipData = ClipData.newRawUri("CSV export", uri)
            startActivity(Intent.createChooser(share, getString(R.string.share_export)))
        }
    }

    private fun runAccountAction(action: suspend () -> Unit) {
        if (accountActionBusy) return
        accountActionBusy = true
        updateAuthUI()
        lifecycleScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                MaterialAlertDialogBuilder(this@SettingsActivity).setTitle(R.string.account_action_failed_title)
                    .setMessage(getString(R.string.account_action_failed, error.message ?: "Please try again."))
                    .setPositiveButton(android.R.string.ok, null).show()
            } finally {
                accountActionBusy = false
                updateAuthUI()
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
                    syncService.syncError.value?.let { throw it }

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
