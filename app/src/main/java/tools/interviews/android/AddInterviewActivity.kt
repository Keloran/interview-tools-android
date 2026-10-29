package tools.interviews.android

import tools.interviews.android.data.api.InterviewCatalog
import tools.interviews.android.data.api.APIStage
import tools.interviews.android.data.api.APIStageMethod
import tools.interviews.android.data.api.APIService
import tools.interviews.android.model.methodLabel
import tools.interviews.android.util.confirmMeetingTime
import tools.interviews.android.model.InterviewTimes
import tools.interviews.android.model.displayInterviewDate
import tools.interviews.android.model.displayDeadline
import tools.interviews.android.model.needsTimeConfirmation
import java.time.ZoneOffset
import tools.interviews.android.util.finishOnAccountChange
import tools.interviews.android.data.account.AccountOwners
import android.app.DatePickerDialog
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
import tools.interviews.android.R

class AddInterviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AddInterviewActivity"
        const val EXTRA_RECRUITER_JSON = "recruiter_json"
        const val EXTRA_PREFILL_APPLICATION = "prefill_application"
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

    private lateinit var recruiterForm: RecruiterForm
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
    private lateinit var editDuration: TextInputEditText
    private lateinit var editNotes: TextInputEditText

    private var selectedStage: InterviewStage = InterviewStage.APPLIED
    private var catalog = InterviewCatalog.fallback()
    private var selectedStageId: Int? = null
    private var selectedStageName: String? = null
    private var selectedStageRecognized = false
    private var stageChanged = false
    private var selectedMethodId: Int? = null
    private var selectedMethodName: String? = null
    private var methodChanged = false
    private var selectedMethod: InterviewMethod? = null
    private var selectedDateTime: LocalDateTime? = null
    private var selectedInstant: Instant? = null
    private var selectedDeadline: LocalDate? = null
    private var initialDate: LocalDate? = null  // Store the date passed from calendar
    private var existingCompanies = mutableListOf<String>()
    private var isNextStageMode = false
    private var screenOwner = AccountOwners.GUEST
    private var previousInterviewId: Long? = null
    private var originalApplicationDate: LocalDate? = null
    private var originalMetadataJSON: String? = null

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    private lateinit var repository: InterviewRepository
    private lateinit var syncService: SyncService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnAccountChange()
        screenOwner = AccountOwners.forUser(Clerk.user?.id)
        setContentView(R.layout.activity_add_interview)

        val app = application as InterviewApplication
        repository = app.repository
        syncService = app.syncService

        // Handle orientation based on fold state (candybar vs tablet mode)
        FoldableOrientationManager(this).attach(this)

        setupViews()
        recruiterForm = RecruiterForm(this)
        setupToolbar()
        loadIntentData()
        recruiterForm.bind(intent.getStringExtra(EXTRA_RECRUITER_JSON))
        setupDropdowns()
        setupCompanyAutocomplete()
        setupDateTimePickers()
        setupValidation()
        updateInterviewDetailsVisibility()
        loadServerCatalog()
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
        editDuration = findViewById(R.id.editDuration)
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

        findViewById<TextInputEditText>(R.id.editClientCompany).setText(intent.getStringExtra(EXTRA_CLIENT_COMPANY).orEmpty())

        if (intent.getBooleanExtra(EXTRA_PREFILL_APPLICATION, false)) {
            editCompanyName.setText(intent.getStringExtra(EXTRA_COMPANY_NAME).orEmpty())
            editJobTitle.setText(intent.getStringExtra(EXTRA_JOB_TITLE).orEmpty())
            editJobListing.setText(intent.getStringExtra(EXTRA_JOB_LISTING).orEmpty())
            editNotes.setText(intent.getStringExtra(EXTRA_NOTES).orEmpty())
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
        val stages = catalog.stages.filter { !isNextStageMode || InterviewCatalog.stageKind(it.stage) != InterviewStage.APPLIED }
        dropdownStage.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, stages.map { it.stage }))
        dropdownStage.setText(selectedStageName ?: selectedStage.displayName, false)
        dropdownStage.setOnItemClickListener { _, _, position, _ ->
            val option = stages[position]
            selectedStage = InterviewCatalog.stageKind(option.stage)
            selectedStageId = option.id.takeIf { it > 0 }
            selectedStageName = option.stage
            selectedStageRecognized = option.id > 0
            stageChanged = true
            updateInterviewDetailsVisibility()
            validateForm()
        }
        dropdownMethod.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, catalog.methods.map { it.method }))
        dropdownMethod.setText(selectedMethodName ?: selectedMethod?.displayName ?: "", false)
        dropdownMethod.setOnItemClickListener { _, _, position, _ ->
            val option = catalog.methods[position]
            selectedMethod = InterviewCatalog.methodKind(option.method)
            selectedMethodId = option.id.takeIf { it > 0 }
            selectedMethodName = option.method
            methodChanged = true
            updateMeetingLinkVisibility()
            validateForm()
        }
        updateMeetingLinkVisibility()
    }

    private fun loadServerCatalog() {
        val app = application as InterviewApplication
        val expected = app.ownerNow()
        if (expected == AccountOwners.GUEST) return
        lifecycleScope.launch {
            try {
                val loaded = app.accountGate.run {
                    app.authenticateAccount(expected)
                    InterviewCatalog(APIService.getInstance().fetchStages(), APIService.getInstance().fetchStageMethods())
                }
                val stages = loaded.stages
                val methods = loaded.methods
                if (app.ownerNow() != expected) return@launch
                if (stages.isNotEmpty()) catalog = InterviewCatalog(stages, methods.ifEmpty { catalog.methods })
                setupDropdowns()
                dropdownStage.isEnabled = true
                dropdownMethod.isEnabled = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                com.google.android.material.snackbar.Snackbar.make(buttonSave, "Connect to load current stage and meeting options.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateMeetingLinkVisibility() {
        // Only show meeting link for Video Call
        val showMeetingLink = selectedMethod == InterviewMethod.VIDEO_CALL || (selectedMethod == null && selectedMethodName != null)
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
            selectedInstant = null
            selectedMethod = null
            selectedMethodId = null
            selectedMethodName = null
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
            selectedDateTime != null -> selectedDateTime!!.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            initialDate != null -> initialDate!!.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            else -> MaterialDatePicker.todayInUtcMilliseconds()
        }

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select interview date")
            .setSelection(initialSelection)
            .build()

        datePicker.addOnPositiveButtonClickListener { dateMillis ->
            val selectedDate = Instant.ofEpochMilli(dateMillis)
                .atZone(ZoneOffset.UTC)
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
                val local = LocalDateTime.of(selectedDate, LocalTime.of(timePicker.hour, timePicker.minute))
                confirmMeetingTime(local) { instant ->
                    selectedInstant = instant
                    selectedDateTime = local
                    editInterviewDateTime.error = null
                    editInterviewDateTime.setText(local.format(dateTimeFormatter))
                    validateForm()
                }
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
        editDuration.doAfterTextChanged { validateForm() }
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
                    (selectedMethod != null || selectedMethodName != null)
            }
        }

        val duration = editDuration.text?.toString()?.trim().orEmpty()
        val validDuration = duration.isEmpty() || duration.toIntOrNull() in 15..480
        editDuration.error = if (validDuration) null else "Enter 15–480 minutes"
        buttonSave.isEnabled = isValid && validDuration
    }

    private fun saveInterview() {
        val recruiterJson = try { recruiterForm.json() } catch (error: IllegalArgumentException) { recruiterForm.showError(error); return }
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
        val outcome = when (selectedStage) {
            InterviewStage.APPLIED -> InterviewOutcome.AWAITING_RESPONSE
            InterviewStage.OFFER -> InterviewOutcome.OFFER_RECEIVED
            else -> InterviewOutcome.SCHEDULED
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
            recruiterContactJSON = recruiterJson, pendingRecruiterJSON = if (recruiterForm.changed) recruiterJson else null,
            clientCompany = findViewById<TextInputEditText>(R.id.editClientCompany).text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            jobTitle = editJobTitle.text.toString().trim(),
            companyName = companyName,
            stage = selectedStage,
            method = selectedMethod,
            outcome = outcome,
            applicationDate = applicationDate,
            interviewInstant = if (selectedStage !in listOf(InterviewStage.APPLIED, InterviewStage.OFFER, InterviewStage.TECHNICAL_TEST)) selectedInstant else null,
            deadlineInstant = deadline?.toLocalDate()?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.minusSeconds(60),
            interviewer = editInterviewer.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            link = link,
            jobListing = jobListing,
            notes = editNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            metadataJSON = originalMetadataJSON,
            previousLocalId = previousInterviewId,
            durationMinutes = editDuration.text?.toString()?.trim()?.toIntOrNull(),
            serverStageId = selectedStageId, serverStageName = selectedStageName,
            stageRecognized = selectedStageRecognized, stageSelectionChanged = stageChanged,
            serverMethodId = selectedMethodId, serverMethodName = selectedMethodName,
            methodSelectionChanged = methodChanged
        )

        // Disable save button and show saving state
        buttonSave.isEnabled = false
        buttonSave.text = "Saving..."

        lifecycleScope.launch {
            try {
                require(!isNextStageMode || previousInterviewId != null) { "Choose the previous stage again." }
                // Step 1: Find or create company locally
                val localCompany = repository.findOrCreateCompany(companyName, screenOwner)
                Log.d(TAG, "Company '${companyName}' has local id: ${localCompany.id}, serverId: ${localCompany.serverId}")

                // Step 2: Create interview with companyId
                val interviewWithCompany = interview.copy(companyId = localCompany.id, ownerId = screenOwner)
                val localId = repository.insert(interviewWithCompany)
                Log.d(TAG, "Interview saved locally with id: $localId")

                // If this is next stage mode, mark the previous interview as passed
                previousInterviewId?.let { prevId ->
                    try {
                        val previousInterview = repository.getById(prevId)
                        if (previousInterview != null) {
                            val updatedPrevious = previousInterview.copy(outcome = InterviewOutcome.PASSED)
                            repository.saveChanges(previousInterview, updatedPrevious)
                            Log.d(TAG, "Previous interview $prevId marked as PASSED")


                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update previous interview: ${e.message}", e)
                    }
                }

                (application as InterviewApplication).schedulePendingSync(screenOwner)

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
