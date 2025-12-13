package tools.interviews.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var itemSignIn: LinearLayout
    private lateinit var buttonSignIn: MaterialButton
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
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        itemSignIn = findViewById(R.id.itemSignIn)
        buttonSignIn = findViewById(R.id.buttonSignIn)
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
            // Sign in will be implemented later
        }

        itemSyncNow.setOnClickListener {
            // Sync will be implemented later (requires sign in first)
        }

        itemWebsite.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://interviews.tools"))
            startActivity(intent)
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
