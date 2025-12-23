package tools.interviews.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.format.DateTimeFormatter

class InterviewDetailActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var buttonEdit: ImageButton
    private lateinit var textJobTitle: TextView
    private lateinit var textCompanyName: TextView
    private lateinit var textClientCompany: TextView
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
    private lateinit var buttonDelete: MaterialButton

    private lateinit var repository: InterviewRepository
    private var interviewId: Long = -1
    private var interview: Interview? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")

    companion object {
        const val EXTRA_INTERVIEW_ID = "interview_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interview_detail)

        repository = (application as InterviewApplication).repository
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
        textJobTitle = findViewById(R.id.textJobTitle)
        textCompanyName = findViewById(R.id.textCompanyName)
        textClientCompany = findViewById(R.id.textClientCompany)
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

        // Client company
        if (interview.clientCompany != null) {
            textClientCompany.text = "via ${interview.clientCompany}"
            textClientCompany.isVisible = true
        } else {
            textClientCompany.isVisible = false
        }

        // Outcome badge
        badgeOutcome.text = interview.outcome.displayName
        badgeOutcome.setBackgroundColor(getOutcomeColor(interview.outcome))
        badgeOutcome.setTextColor(getOutcomeTextColor(interview.outcome))

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
}
