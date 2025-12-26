package tools.interviews.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.data.api.APIService
import tools.interviews.android.data.api.SyncService
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.format.DateTimeFormatter

class InterviewDetailActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var buttonEdit: ImageButton
    private lateinit var buttonForward: ImageButton
    private lateinit var buttonReject: ImageButton

    private lateinit var buttonAwaiting: ImageButton
    private lateinit var textJobTitle: TextView
    private lateinit var textCompanyName: TextView
    private lateinit var badgeOutcome: TextView
    private lateinit var badgeStage: TextView
    private lateinit var badgeMethod: TextView
    private lateinit var labelDateTime: TextView
    private lateinit var cardDateTime: MaterialCardView
    private lateinit var textDateTime: TextView
    private lateinit var labelDeadline: TextView
    private lateinit var cardDeadline: MaterialCardView
    private lateinit var textDeadline: TextView
    private lateinit var labelInterviewer: TextView
    private lateinit var cardInterviewer: MaterialCardView
    private lateinit var textInterviewer: TextView
    private lateinit var labelLink: TextView
    private lateinit var cardLink: MaterialCardView
    private lateinit var textLink: TextView
    private lateinit var labelJobListing: TextView
    private lateinit var cardJobListing: MaterialCardView
    private lateinit var textJobListing: TextView
    private lateinit var labelNotes: TextView
    private lateinit var cardNotes: MaterialCardView
    private lateinit var textNotes: TextView
    private lateinit var textApplicationDate: TextView

    private lateinit var repository: InterviewRepository
    private lateinit var syncService: SyncService
    private var interviewId: Long = -1
    private var interview: Interview? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")

    private val nextStageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Interview saved in AddInterviewActivity, will reload on resume */ }

    companion object {
        private const val TAG = "InterviewDetailActivity"
        const val EXTRA_INTERVIEW_ID = "interview_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interview_detail)

        val app = application as InterviewApplication
        repository = app.repository
        syncService = app.syncService
        interviewId = intent.getLongExtra(EXTRA_INTERVIEW_ID, -1)

        if (interviewId == -1L) {
            finish()
            return
        }

        setupViews()
        setupToolbar()
        loadInterview()
    }

    override fun onResume() {
        super.onResume()
        // Reload interview data when returning from edit
        loadInterview()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        buttonEdit = findViewById(R.id.buttonEdit)
        buttonForward = findViewById(R.id.buttonForward)
        buttonReject = findViewById(R.id.buttonReject)
        buttonAwaiting = findViewById(R.id.buttonAwaiting)
        textJobTitle = findViewById(R.id.textJobTitle)
        textCompanyName = findViewById(R.id.textCompanyName)
        badgeOutcome = findViewById(R.id.badgeOutcome)
        badgeStage = findViewById(R.id.badgeStage)
        badgeMethod = findViewById(R.id.badgeMethod)
        labelDateTime = findViewById(R.id.labelDateTime)
        cardDateTime = findViewById(R.id.cardDateTime)
        textDateTime = findViewById(R.id.textDateTime)
        labelDeadline = findViewById(R.id.labelDeadline)
        cardDeadline = findViewById(R.id.cardDeadline)
        textDeadline = findViewById(R.id.textDeadline)
        labelInterviewer = findViewById(R.id.labelInterviewer)
        cardInterviewer = findViewById(R.id.cardInterviewer)
        textInterviewer = findViewById(R.id.textInterviewer)
        labelLink = findViewById(R.id.labelLink)
        cardLink = findViewById(R.id.cardLink)
        textLink = findViewById(R.id.textLink)
        labelJobListing = findViewById(R.id.labelJobListing)
        cardJobListing = findViewById(R.id.cardJobListing)
        textJobListing = findViewById(R.id.textJobListing)
        labelNotes = findViewById(R.id.labelNotes)
        cardNotes = findViewById(R.id.cardNotes)
        textNotes = findViewById(R.id.textNotes)
        textApplicationDate = findViewById(R.id.textApplicationDate)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }

        buttonEdit.setOnClickListener {
            interview?.let { editInterview(it) }
        }

        buttonForward.setOnClickListener {
            interview?.let { launchNextStage(it) }
        }

        buttonReject.setOnClickListener {
            interview?.let {
                updateInterviewOutcome(it, InterviewOutcome.REJECTED)
                Snackbar.make(toolbar, "Status: Rejected", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadInterview() {
        lifecycleScope.launch {
            interview = repository.getById(interviewId)
            interview?.let { displayInterview(it) } ?: finish()
        }
    }

    private fun displayInterview(interview: Interview) {
        // Basic info
        textJobTitle.text = interview.jobTitle
        textCompanyName.text = interview.companyName

        // Outcome badge
        badgeOutcome.text = interview.outcome.displayName
        badgeOutcome.setBackgroundColor(getOutcomeColor(interview.outcome))
        badgeOutcome.setTextColor(getOutcomeTextColor(interview.outcome))

        // Hide action buttons if outcome is final (rejected or passed)
        val isOutcomeFinal = interview.outcome == InterviewOutcome.REJECTED ||
                interview.outcome == InterviewOutcome.PASSED

        // Default to invisible
        buttonReject.isVisible = false
        buttonForward.isVisible = false
        buttonAwaiting.isVisible = false

        lifecycleScope.launch {
            val client = (application as InterviewApplication).flagsClient.await()

            if (!isOutcomeFinal) {
                buttonReject.isVisible = client.isEnabled("reject button")
                buttonForward.isVisible = client.isEnabled("approve button")
                buttonAwaiting.isVisible = client.isEnabled("awaiting button")
            }
        }

        // Stage badge
        badgeStage.text = interview.stage.displayName

        // Method badge
        if (interview.method != null) {
            badgeMethod.text = interview.method.displayName
            badgeMethod.isVisible = true
        } else {
            badgeMethod.isVisible = false
        }

        // Interview date/time
        if (interview.interviewDate != null) {
            labelDateTime.text = "Interview Date & Time"
            labelDateTime.isVisible = true
            cardDateTime.isVisible = true
            textDateTime.text = interview.interviewDate.format(dateTimeFormatter)
        } else {
            labelDateTime.isVisible = false
            cardDateTime.isVisible = false
        }

        // Deadline (for technical tests)
        if (interview.deadline != null) {
            labelDeadline.isVisible = true
            cardDeadline.isVisible = true
            textDeadline.text = interview.deadline.format(dateFormatter)
        } else {
            labelDeadline.isVisible = false
            cardDeadline.isVisible = false
        }

        // Interviewer
        if (interview.interviewer != null) {
            labelInterviewer.isVisible = true
            cardInterviewer.isVisible = true
            textInterviewer.text = interview.interviewer
        } else {
            labelInterviewer.isVisible = false
            cardInterviewer.isVisible = false
        }

        // Meeting/Test link
        if (interview.link != null) {
            labelLink.text = if (interview.stage == InterviewStage.TECHNICAL_TEST) "Test Link" else "Meeting Link"
            labelLink.isVisible = true
            cardLink.isVisible = true
            textLink.text = interview.link

            // Make link clickable
            cardLink.setOnClickListener {
                openLink(interview.link)
            }
        } else {
            labelLink.isVisible = false
            cardLink.isVisible = false
        }

        // Job Listing
        if (interview.jobListing != null) {
            labelJobListing.isVisible = true
            cardJobListing.isVisible = true
            textJobListing.text = interview.jobListing

            // Make job listing link clickable
            cardJobListing.setOnClickListener {
                openLink(interview.jobListing)
            }
        } else {
            labelJobListing.isVisible = false
            cardJobListing.isVisible = false
        }

        // Notes
        if (interview.notes != null) {
            labelNotes.isVisible = true
            cardNotes.isVisible = true
            textNotes.text = interview.notes
        } else {
            labelNotes.isVisible = false
            cardNotes.isVisible = false
        }

        // Application date
        textApplicationDate.text = interview.applicationDate.format(dateFormatter)
    }

    private fun getOutcomeColor(outcome: InterviewOutcome): Int {
        return when (outcome) {
            InterviewOutcome.SCHEDULED -> getColor(R.color.outcome_scheduled)
            InterviewOutcome.AWAITING_RESPONSE -> getColor(R.color.outcome_awaiting)
            InterviewOutcome.PASSED -> getColor(R.color.outcome_passed)
            InterviewOutcome.REJECTED -> getColor(R.color.outcome_rejected)
            InterviewOutcome.OFFER_ACCEPTED -> getColor(R.color.outcome_offer_accepted)
            InterviewOutcome.OFFER_RECEIVED -> getColor(R.color.outcome_offer_received)
            InterviewOutcome.OFFER_DECLINED -> getColor(R.color.outcome_offer_declined)
            InterviewOutcome.WITHDREW -> getColor(R.color.outcome_withdrew)
        }
    }

    private fun getOutcomeTextColor(outcome: InterviewOutcome): Int {
        return when (outcome) {
            InterviewOutcome.AWAITING_RESPONSE -> getColor(R.color.black)
            else -> getColor(R.color.white)
        }
    }

    private fun openLink(url: String) {
        try {
            val uri = if (url.startsWith("http://") || url.startsWith("https://")) {
                Uri.parse(url)
            } else {
                Uri.parse("https://$url")
            }
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Snackbar.make(textLink, "Unable to open link", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun editInterview(interview: Interview) {
        val intent = Intent(this, EditInterviewActivity::class.java).apply {
            putExtra(EditInterviewActivity.EXTRA_INTERVIEW_ID, interview.id)
        }
        startActivity(intent)
    }

    private fun updateInterviewOutcome(interview: Interview, newOutcome: InterviewOutcome) {
        lifecycleScope.launch {
            val updatedInterview = interview.copy(outcome = newOutcome)
            repository.update(updatedInterview)
            this@InterviewDetailActivity.interview = updatedInterview
            displayInterview(updatedInterview)

            // If interview has been synced to server, update remotely too
            if (interview.serverId != null) {
                try {
                    val session = Clerk.sessionFlow.value
                    val token = session?.fetchToken()?.successOrNull()

                    if (token != null) {
                        APIService.getInstance().setAuthToken(token.jwt)
                        syncService.updateRemoteInterview(interview.serverId, updatedInterview)
                        Log.d(TAG, "Updated interview outcome on server: ${newOutcome.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update outcome on server: ${e.message}", e)
                }
            }
        }
    }

    private fun launchNextStage(interview: Interview) {
        val intent = Intent(this, AddInterviewActivity::class.java).apply {
            putExtra(AddInterviewActivity.EXTRA_NEXT_STAGE_MODE, true)
            putExtra(AddInterviewActivity.EXTRA_PREVIOUS_INTERVIEW_ID, interview.id)
            putExtra(AddInterviewActivity.EXTRA_COMPANY_NAME, interview.companyName)
            putExtra(AddInterviewActivity.EXTRA_JOB_TITLE, interview.jobTitle)
            putExtra(AddInterviewActivity.EXTRA_JOB_LISTING, interview.jobListing)
            putExtra(AddInterviewActivity.EXTRA_APPLICATION_DATE, interview.applicationDate.toString())
            putExtra(AddInterviewActivity.EXTRA_NOTES, interview.notes)
            putExtra(AddInterviewActivity.EXTRA_METADATA_JSON, interview.metadataJSON)
        }
        nextStageLauncher.launch(intent)
    }
}
