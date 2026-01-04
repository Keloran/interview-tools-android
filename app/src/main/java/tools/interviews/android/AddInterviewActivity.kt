package tools.interviews.android

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.data.api.APIService
import tools.interviews.android.data.api.SyncService
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import tools.interviews.android.util.FoldableOrientationManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AddInterviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AddInterviewActivity"
        const val EXTRA_SELECTED_DATE = "selected_date"
        const val EXTRA_INTERVIEW = "interview"
        const val EXTRA_COMPANIES = "companies"
        const val EXTRA_NEW_COMPANY = "new_company"
        const val EXTRA_NEXT_STAGE_MODE = "next_stage_mode"
        const val EXTRA_COMPANY_NAME = "company_name"
        const val EXTRA_CLIENT_COMPANY = "client_company"
        const val EXTRA_JOB_TITLE = "job_title"
        const val EXTRA_JOB_LISTING = "job_listing"
        const val EXTRA_APPLICATION_DATE = "application_date"
        const val EXTRA_NOTES = "notes"
        const val EXTRA_METADATA_JSON = "metadata_json"
        const val EXTRA_PREVIOUS_INTERVIEW_ID = "previous_interview_id"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var buttonSave: MaterialButton
    private lateinit var dropdownStage: AutoCompleteTextView
    private lateinit var editCompanyName: AutoCompleteTextView
    private lateinit var editJobTitle: TextInputEditText
    private lateinit var editJobListing: TextInputEditText
    private lateinit var sectionInterviewDetails: LinearLayout
    private lateinit var editInterviewDateTime: TextInputEditText
    private lateinit var dropdownMethod: AutoCompleteTextView
    private lateinit var editInterviewer: TextInputEditText
    private lateinit var layoutMeetingLink: TextInputLayout
    private lateinit var editMeetingLink: TextInputEditText
    private lateinit var sectionDeadline: LinearLayout
    private lateinit var editDeadline: TextInputEditText
    private lateinit var editTestLink: TextInputEditText
    private lateinit var editNotes: TextInputEditText

    private var selectedStage: InterviewStage = InterviewStage.APPLIED
    private var selectedMethod: InterviewMethod? = null
    private var selectedDateTime: LocalDateTime? = null
    private var selectedDeadline: LocalDate? = null
    private var initialDate: LocalDate? = null  // Store the date passed from calendar
    private var existingCompanies = mutableListOf<String>()
    private var isNextStageMode = false
    private var previousInterviewId: Long? = null
    private var originalApplicationDate: LocalDate? = null
    private var originalMetadataJSON: String? = null

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    private lateinit var repository: InterviewRepository
    private lateinit var syncService: SyncService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_interview)

        val app = application as InterviewApplication
        repository = app.repository
        syncService = app.syncService

        // Handle orientation based on fold state (candybar vs tablet mode)
        FoldableOrientationManager(this).attach(this)

        setupViews()
        setupToolbar()
        loadIntentData()
        setupDropdowns()
        setupCompanyAutocomplete()
        setupDateTimePickers()
        setupValidation()
        updateInterviewDetailsVisibility()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        buttonSave = findViewById(R.id.buttonSave)
        dropdownStage = findViewById(R.id.dropdownStage)
        editCompanyName = findViewById(R.id.editCompanyName)
        editJobTitle = findViewById(R.id.editJobTitle)
        editJobListing = findViewById(R.id.editJobListing)
        sectionInterviewDetails = findViewById(R.id.sectionInterviewDetails)
        editInterviewDateTime = findViewById(R.id.editInterviewDateTime)
        dropdownMethod = findViewById(R.id.dropdownMethod)
        editInterviewer = findViewById(R.id.editInterviewer)
        layoutMeetingLink = findViewById(R.id.layoutMeetingLink)
        editMeetingLink = findViewById(R.id.editMeetingLink)
        sectionDeadline = findViewById(R.id.sectionDeadline)
        editDeadline = findViewById(R.id.editDeadline)
        editTestLink = findViewById(R.id.editTestLink)
        editNotes = findViewById(R.id.editNotes)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }

        buttonSave.setOnClickListener {
            saveInterview()
        }
    }

    private fun loadIntentData() {
        // Load existing companies
        intent.getStringArrayListExtra(EXTRA_COMPANIES)?.let {
            existingCompanies.addAll(it)
        }

        // Pre-fill date if passed from main activity
        intent.getStringExtra(EXTRA_SELECTED_DATE)?.let { dateString ->
            initialDate = LocalDate.parse(dateString)
        }

        // Check if this is next stage mode
        isNextStageMode = intent.getBooleanExtra(EXTRA_NEXT_STAGE_MODE, false)
        if (isNextStageMode) {
            // Update toolbar title
            toolbar.title = "Next Stage"

            // Get previous interview ID to mark as passed after saving
            previousInterviewId = intent.getLongExtra(EXTRA_PREVIOUS_INTERVIEW_ID, -1L)
                .takeIf { it != -1L }

            // Pre-fill all metadata from previous interview
            intent.getStringExtra(EXTRA_COMPANY_NAME)?.let {
                editCompanyName.setText(it)
            }
            intent.getStringExtra(EXTRA_JOB_TITLE)?.let {
                editJobTitle.setText(it)
            }
            intent.getStringExtra(EXTRA_JOB_LISTING)?.let {
                editJobListing.setText(it)
            }
            intent.getStringExtra(EXTRA_NOTES)?.let {
                editNotes.setText(it)
            }
            intent.getStringExtra(EXTRA_APPLICATION_DATE)?.let {
                originalApplicationDate = LocalDate.parse(it)
            }
            originalMetadataJSON = intent.getStringExtra(EXTRA_METADATA_JSON)

            // Default to first stage that isn't Applied (Phone Screen)
            selectedStage = InterviewStage.PHONE_SCREEN
        }
    }

    private fun setupDropdowns() {
        // Stage dropdown - exclude Applied in next stage mode
        val availableStages = if (isNextStageMode) {
            InterviewStage.entries.filter { it != InterviewStage.APPLIED }
        } else {
            InterviewStage.entries.toList()
        }
        val stages = availableStages.map { it.displayName }
        val stageAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, stages)
        dropdownStage.setAdapter(stageAdapter)
        dropdownStage.setText(selectedStage.displayName, false)
        dropdownStage.setOnItemClickListener { _, _, position, _ ->
            selectedStage = availableStages[position]
            updateInterviewDetailsVisibility()
            validateForm()
        }

        // Method dropdown
        val methods = InterviewMethod.entries.map { it.displayName }
        val methodAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, methods)
        dropdownMethod.setAdapter(methodAdapter)
        dropdownMethod.setOnItemClickListener { _, _, position, _ ->
            selectedMethod = InterviewMethod.entries[position]
            updateMeetingLinkVisibility()
            validateForm()
        }
    }

    private fun updateMeetingLinkVisibility() {
        // Only show meeting link for Video Call
        val showMeetingLink = selectedMethod == InterviewMethod.VIDEO_CALL
        layoutMeetingLink.isVisible = showMeetingLink
        if (!showMeetingLink) {
            editMeetingLink.text?.clear()
        }
    }

    private fun setupCompanyAutocomplete() {
        val companyAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            existingCompanies
        )
        editCompanyName.setAdapter(companyAdapter)
        editCompanyName.threshold = 1
    }

    private fun updateInterviewDetailsVisibility() {
        val isTechnicalTest = selectedStage == InterviewStage.TECHNICAL_TEST
        val isAppliedOrOffer = selectedStage == InterviewStage.APPLIED ||
                              selectedStage == InterviewStage.OFFER

        // Show interview details for normal interview stages (not Applied, Offer, or Technical Test)
        val showInterviewDetails = !isAppliedOrOffer && !isTechnicalTest
        sectionInterviewDetails.isVisible = showInterviewDetails

        // Show deadline section only for Technical Test
        sectionDeadline.isVisible = isTechnicalTest

        if (showInterviewDetails) {
            // Show existing date/time if selected
            selectedDateTime?.let {
                editInterviewDateTime.setText(it.format(dateTimeFormatter))
            }
        } else if (isTechnicalTest) {
            // For Technical Test, restore deadline if available
            if (selectedDeadline == null && initialDate != null) {
                selectedDeadline = initialDate
            }
            selectedDeadline?.let {
                editDeadline.setText(it.format(dateFormatter))
            }
        }

        if (!showInterviewDetails) {
            // Clear interview UI details if hiding
            selectedDateTime = null
            selectedMethod = null
            editInterviewDateTime.text?.clear()
            dropdownMethod.text?.clear()
            editInterviewer.text?.clear()
            editMeetingLink.text?.clear()
        }

        if (!isTechnicalTest) {
            // Clear deadline details if not Technical Test
            selectedDeadline = null
            editDeadline.text?.clear()
            editTestLink.text?.clear()
        }
    }

    private fun setupDateTimePickers() {
        editInterviewDateTime.setOnClickListener {
            showDateTimePicker()
        }

        editDeadline.setOnClickListener {
            showDeadlinePicker()
        }
    }

    private fun showDateTimePicker() {
        // Use initial date if available, or existing selection, or today
        val initialSelection = when {
            selectedDateTime != null -> selectedDateTime!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            initialDate != null -> initialDate!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            else -> MaterialDatePicker.todayInUtcMilliseconds()
        }

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select interview date")
            .setSelection(initialSelection)
            .build()

        datePicker.addOnPositiveButtonClickListener { dateMillis ->
            val selectedDate = Instant.ofEpochMilli(dateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            // Immediately show time picker after date is selected
            val initialHour = selectedDateTime?.hour ?: 9
            val initialMinute = selectedDateTime?.minute ?: 0

            val timePicker = MaterialTimePicker.Builder()
                .setTitleText("Select interview time")
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(initialHour)
                .setMinute(initialMinute)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                selectedDateTime = LocalDateTime.of(
                    selectedDate,
                    LocalTime.of(timePicker.hour, timePicker.minute)
                )
                editInterviewDateTime.setText(selectedDateTime?.format(dateTimeFormatter))
                validateForm()
            }

            timePicker.show(supportFragmentManager, "timePicker")
        }

        datePicker.show(supportFragmentManager, "datePicker")
    }

    private fun showDeadlinePicker() {
        val initial = selectedDeadline ?: LocalDate.now().plusDays(7)
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDeadline = LocalDate.of(year, month + 1, dayOfMonth)
                editDeadline.setText(selectedDeadline?.format(dateFormatter))
                validateForm()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        )
        // Set minimum date to today (deadline must be in the future)
        dialog.datePicker.minDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun setupValidation() {
        editCompanyName.doAfterTextChanged { validateForm() }
        editJobTitle.doAfterTextChanged { validateForm() }
    }

    private fun validateForm() {
        val hasBasicInfo = editCompanyName.text?.isNotBlank() == true &&
                editJobTitle.text?.isNotBlank() == true

        val isValid = when {
            selectedStage == InterviewStage.APPLIED ||
            selectedStage == InterviewStage.OFFER -> {
                // For Applied/Offer, only need company and job title
                hasBasicInfo
            }
            selectedStage == InterviewStage.TECHNICAL_TEST -> {
                // For Technical Test, need company, job title, and deadline
                hasBasicInfo && selectedDeadline != null
            }
            else -> {
                // For other stages, need interview details (date/time and method)
                hasBasicInfo &&
                    selectedDateTime != null &&
                    selectedMethod != null
            }
        }

        buttonSave.isEnabled = isValid
    }

    private fun saveInterview() {
        val companyName = editCompanyName.text.toString().trim()
        val isTechnicalTest = selectedStage == InterviewStage.TECHNICAL_TEST

        // Use selected date/time from the picker
        val interviewDate = selectedDateTime

        // For Technical Test, deadline is stored at end of day
        val deadline = if (isTechnicalTest && selectedDeadline != null) {
            selectedDeadline!!.atTime(23, 59)
        } else {
            null
        }

        // Set outcome based on stage
        val outcome = if (selectedStage == InterviewStage.APPLIED) {
            InterviewOutcome.AWAITING_RESPONSE
        } else {
            InterviewOutcome.SCHEDULED
        }

        // For Technical Test, use the test link; otherwise use meeting link
        val link = if (isTechnicalTest) {
            editTestLink.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            editMeetingLink.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }

        val jobListing = editJobListing.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        // Use original application date if in next stage mode, selected date from calendar, or today
        val applicationDate = originalApplicationDate ?: initialDate ?: LocalDate.now()

        // Create interview with id = 0 to let Room auto-generate
        val interview = Interview(
            id = 0,
            serverId = null, // Not synced yet
            jobTitle = editJobTitle.text.toString().trim(),
            companyName = companyName,
            stage = selectedStage,
            method = selectedMethod,
            outcome = outcome,
            applicationDate = applicationDate,
            interviewDate = interviewDate,
            deadline = deadline,
            interviewer = editInterviewer.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            link = link,
            jobListing = jobListing,
            notes = editNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            metadataJSON = originalMetadataJSON
        )

        // Disable save button and show saving state
        buttonSave.isEnabled = false
        buttonSave.text = "Saving..."

        lifecycleScope.launch {
            try {
                // Step 1: Find or create company locally
                val localCompany = repository.findOrCreateCompany(companyName)
                Log.d(TAG, "Company '${companyName}' has local id: ${localCompany.id}, serverId: ${localCompany.serverId}")

                // Step 2: Create interview with companyId
                val interviewWithCompany = interview.copy(companyId = localCompany.id)
                val localId = repository.insert(interviewWithCompany)
                Log.d(TAG, "Interview saved locally with id: $localId")

                // Step 3: If user is signed in, push to server
                val user = Clerk.user
                if (user != null) {
                    try {
                        val session = Clerk.sessionFlow.value
                        val token = session?.fetchToken()?.successOrNull()

                        if (token != null) {
                            APIService.getInstance().setAuthToken(token.jwt)

                            // Get the interview with local ID for pushing
                            val savedInterview = interviewWithCompany.copy(id = localId)

                            // Push to server
                            val apiInterview = syncService.pushInterview(savedInterview)
                            Log.d(TAG, "Interview pushed to server with id: ${apiInterview.id}")
                            Log.d(TAG, "Server returned company id: ${apiInterview.company.id}")

                            // Step 4: Update local company with server ID (if it didn't have one)
                            if (localCompany.serverId == null) {
                                repository.updateCompanyServerId(localCompany.id, apiInterview.company.id)
                                Log.d(TAG, "Updated local company with server id: ${apiInterview.company.id}")
                            }

                            // Step 5: Update local interview with server ID
                            val updatedInterview = savedInterview.copy(serverId = apiInterview.id)
                            repository.update(updatedInterview)
                            Log.d(TAG, "Local interview updated with server id: ${apiInterview.id}")

                            Snackbar.make(buttonSave, "Interview saved and synced", Snackbar.LENGTH_SHORT).show()
                        } else {
                            Log.w(TAG, "No auth token available, interview saved locally only")
                        }
                    } catch (e: Exception) {
                        // Server push failed, but local save succeeded - that's ok for local-first
                        Log.e(TAG, "Failed to push to server: ${e.message}", e)
                        Snackbar.make(buttonSave, "Saved locally (sync failed)", Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d(TAG, "User not signed in, interview saved locally only")
                }

                // If this is next stage mode, mark the previous interview as passed
                previousInterviewId?.let { prevId ->
                    try {
                        val previousInterview = repository.getById(prevId)
                        if (previousInterview != null) {
                            val updatedPrevious = previousInterview.copy(outcome = InterviewOutcome.PASSED)
                            repository.update(updatedPrevious)
                            Log.d(TAG, "Previous interview $prevId marked as PASSED")

                            // Sync to server if previously synced
                            if (previousInterview.serverId != null && Clerk.user != null) {
                                try {
                                    syncService.updateRemoteInterview(previousInterview.serverId, updatedPrevious)
                                    Log.d(TAG, "Previous interview updated on server")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to update previous interview on server: ${e.message}", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update previous interview: ${e.message}", e)
                    }
                }

                // Return success
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save interview: ${e.message}", e)
                buttonSave.isEnabled = true
                buttonSave.text = "Save"
                Snackbar.make(buttonSave, "Failed to save: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
