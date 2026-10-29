package tools.interviews.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tools.interviews.android.data.api.*
import tools.interviews.android.util.finishOnAccountChange

class JobCheckActivity : AppCompatActivity() {
    private val app get() = application as InterviewApplication
    private lateinit var company: TextInputEditText
    private lateinit var client: TextInputEditText
    private lateinit var title: TextInputEditText
    private lateinit var link: TextInputEditText
    private lateinit var description: TextInputEditText
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var check: MaterialButton
    private var revision = 0
    private var request: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnAccountChange()
        setContentView(R.layout.activity_job_check)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        company = findViewById(R.id.editCheckCompany)
        client = findViewById(R.id.editCheckClient)
        title = findViewById(R.id.editCheckTitle)
        link = findViewById(R.id.editCheckLink)
        description = findViewById(R.id.editCheckDescription)
        status = findViewById(R.id.textCheckStatus)
        results = findViewById(R.id.checkResults)
        check = findViewById(R.id.buttonCheckJob)
        if (savedInstanceState == null && intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val listing = SharedListings.parse(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(), intent.getStringExtra(Intent.EXTRA_SUBJECT))
            title.setText(listing.title); link.setText(listing.link); description.setText(listing.description)
            if (listing.truncated) status.text = "Shared text was shortened to 100,000 characters. Review it before checking."
        }
        for (field in listOf(company, client, title, link, description)) field.doAfterTextChanged {
            revision++; request?.cancel(); results.removeAllViews()
            status.text = "Check again to see results for these fields."
            check.isEnabled = true
        }
        check.setOnClickListener { checkListing() }
        findViewById<MaterialButton>(R.id.buttonAddCheckedJob).setOnClickListener { addApplication() }
    }
    private fun input() = JobCheckInput(link.text.toString().trim(), title.text.toString().trim(), company.text.toString().trim(), client.text.toString().trim(), description.text.toString().trim())
    private fun checkListing() {
        val input = input()
        val body = try { input.json() } catch (e: IllegalArgumentException) { status.text = e.message; return }
        val owner = app.ownerNow()
        val started = revision
        results.removeAllViews(); status.text = "Checking your history…"; check.isEnabled = false
        request = lifecycleScope.launch {
            try {
                val response = app.authenticatedRequests.run(owner) { APIService.getInstance().checkJob(body) }
                if (revision != started) return@launch
                val result = JobCheckResult.parse(response)
                status.text = "${result.matches.size} previous application(s). " + if (input.description.isBlank()) "Paste listing text to check its wording; the URL alone doesn’t include it." else "Warnings are based on the pasted text and your recorded history."
                if (result.matches.isEmpty()) card("No matching applications", "No recorded applications matched these fields. This does not confirm you have never applied.")
                for (warning in result.warnings) card(warning.title, listOf(warning.explanation, warning.evidence).filter { it.isNotBlank() }.joinToString("\n"))
                for (match in result.matches) {
                    val heading = match.title + when { match.samePosting -> " · Same job URL"; match.sameTitle -> " · Matching role"; else -> "" }
                    card(heading, listOf(match.company, match.client.takeIf { it.isNotBlank() }?.let { "Client: $it" }.orEmpty(), "Applied ${match.applied}", match.status).filter { it.isNotBlank() }.joinToString("\n"), match.id)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { if (revision == started) status.text = "Couldn’t check your history. ${e.message ?: "Check your connection and retry."}" }
            finally { if (revision == started) check.isEnabled = true }
        }
    }
    private fun card(heading: String, detail: String, applicationId: Int? = null) {
        val card = MaterialCardView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        content.addView(TextView(this).apply { text = heading; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium) })
        content.addView(TextView(this).apply { text = detail; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium); setTextIsSelectable(true) })
        if (applicationId != null && applicationId > 0) content.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Open application on website"
            setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://interviews.tools/applications/$applicationId"))) } }
        })
        card.addView(content)
        results.addView(card, LinearLayout.LayoutParams(-1,-2).apply { topMargin = 16 })
    }
    private fun addApplication() {
        val data = input()
        startActivity(Intent(this, AddInterviewActivity::class.java)
            .putExtra(AddInterviewActivity.EXTRA_PREFILL_APPLICATION, true)
            .putExtra(AddInterviewActivity.EXTRA_COMPANY_NAME, data.company)
            .putExtra(AddInterviewActivity.EXTRA_CLIENT_COMPANY, data.client)
            .putExtra(AddInterviewActivity.EXTRA_JOB_TITLE, data.title)
            .putExtra(AddInterviewActivity.EXTRA_JOB_LISTING, data.link)
            .putExtra(AddInterviewActivity.EXTRA_NOTES, data.description))
    }
}
