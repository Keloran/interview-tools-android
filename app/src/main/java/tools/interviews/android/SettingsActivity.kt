package tools.interviews.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var itemSignIn: LinearLayout
    private lateinit var buttonSignIn: MaterialButton
    private lateinit var textAccountTitle: TextView
    private lateinit var textAccountSubtitle: TextView
    private lateinit var itemSyncNow: LinearLayout
    private lateinit var textLastSynced: TextView
    private lateinit var itemWebsite: LinearLayout
    private lateinit var textVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupViews()
        setupToolbar()
        setupClickListeners()
        displayVersion()
        observeAuthState()
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
            // Sync will be implemented later (requires sign in first)
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
            itemSyncNow.alpha = 1.0f
            itemSyncNow.isEnabled = true
        } else {
            // User is not signed in (guest mode)
            textAccountTitle.text = "Guest Mode"
            textAccountSubtitle.text = "Sign in to sync across devices"
            buttonSignIn.text = "Sign In"
            itemSyncNow.alpha = 0.5f
            itemSyncNow.isEnabled = false
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
}
