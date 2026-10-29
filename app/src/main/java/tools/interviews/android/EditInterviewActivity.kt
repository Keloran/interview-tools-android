package tools.interviews.android

import tools.interviews.android.data.api.InterviewCatalog
import tools.interviews.android.data.api.APIStage
import tools.interviews.android.data.api.APIStageMethod
import tools.interviews.android.data.api.APIService
import tools.interviews.android.model.methodLabel
import tools.interviews.android.model.*
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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tools.interviews.android.data.InterviewRepository
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

class EditInterviewActivity : AppCompatActivity() {

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

    private lateinit var repository: InterviewRepository
    private var interviewId: Long = -1
    private var originalInterview: Interview? = null

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
    private var selectedOutcome: InterviewOutcome = InterviewOutcome.SCHEDULED
    private var selectedDateTime: LocalDateTime? = null
    private var selectedInstant: Instant? = null
    private var selectedDeadline: LocalDate? = null
    private var deadlineChanged = false
    private var applicationDate: LocalDate = LocalDate.now()
    private var existingCompanies = mutableListOf<String>()

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    companion object {
        const val EXTRA_INTERVIEW_ID = "interview_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishOnAccountChange()
        setContentView(R.layout.activity_add_interview)

        repository = (application as InterviewApplication).repository
        interviewId = intent.getLongExtra(EXTRA_INTERVIEW_ID, -1)

        // Handle orientation based on fold state (candybar vs tablet mode)
        FoldableOrientationManager(this).attach(this)

        if (interviewId == -1L) {
            finish()
            return
        }

        setupViews()
        setupToolbar()
        loadData()
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

        toolbar.title = "Edit Interview"
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }

        buttonSave.setOnClickListener {
            saveInterview()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            // Load companies for autocomplete
            existingCompanies.addAll(repository.companyNames())
            setupCompanyAutocomplete()

            // Load the interview
            originalInterview = repository.getById(interviewId)
            originalInterview?.let { interview ->
                populateForm(interview)
                setupDropdowns()
                setupDateTimePickers()
                setupValidation()
                updateInterviewDetailsVisibility(clearHiddenFields = false)
                if ((application as InterviewApplication).ownerNow() != AccountOwners.GUEST) {
                    dropdownStage.isEnabled = false
                    dropdownMethod.isEnabled = false
                    loadServerCatalog()
                }
            } ?: finish()
        }
    }

    private fun populateForm(interview: Interview) {
        selectedStage = interview.stage
        selectedMethod = interview.method
        selectedStageId = interview.serverStageId
        selectedStageName = interview.serverStageName
        selectedStageRecognized = interview.stageRecognized
        selectedMethodId = interview.serverMethodId
        selectedMethodName = interview.serverMethodName
        editDuration.setText(interview.durationMinutes?.toString() ?: "")
        selectedOutcome = interview.outcome
        applicationDate = interview.applicationDate

        editCompanyName.setText(interview.companyName)
        editJobTitle.setText(interview.jobTitle)
        editJobListing.setText(interview.jobListing ?: "")

        selectedInstant = interview.interviewInstant
        (interview.interviewInstant?.let { InterviewTimes.local(it) } ?: interview.interviewDate)?.let {
            selectedDateTime = it
            editInterviewDateTime.setText(it.format(dateTimeFormatter))
        }

        interview.displayDeadline?.let {
            selectedDeadline = it.toLocalDate()
            editDeadline.setText(selectedDeadline?.format(dateFormatter))
        }

        editInterviewer.setText(interview.interviewer ?: "")

        if (interview.stage == InterviewStage.TECHNICAL_TEST) {
            editTestLink.setText(interview.link ?: "")
        } else {
            editMeetingLink.setText(interview.link ?: "")
        }

        editNotes.setText(interview.notes ?: "")
        if (interview.needsTimeConfirmation) {
            editInterviewDateTime.error = "Older times have no timezone. Pick the meeting time again before syncing."
            editDeadline.error = "Pick the deadline again to confirm its timezone."
        }
    }

    private fun setupDropdowns() {
        val stages = catalog.stages
        dropdownStage.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, stages.map { it.stage }))
        dropdownStage.setText(selectedStageName ?: originalInterview?.stageLabel ?: selectedStage.displayName, false)
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
        updateMeetingLinkVisibility(clearHiddenFields = false)
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

    private fun updateMeetingLinkVisibility(clearHiddenFields: Boolean = true) {
        // Only show meeting link for Video Call
        val showMeetingLink = selectedMethod == InterviewMethod.VIDEO_CALL || (selectedMethod == null && selectedMethodName != null)
        layoutMeetingLink.isVisible = showMeetingLink
        if (!showMeetingLink && clearHiddenFields) {
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

    private fun updateInterviewDetailsVisibility(clearHiddenFields: Boolean = true) {
        val isTechnicalTest = selectedStage == InterviewStage.TECHNICAL_TEST
        val isAppliedOrOffer = selectedStage == InterviewStage.APPLIED ||
                              selectedStage == InterviewStage.OFFER

        // Show interview details for normal interview stages
        val showInterviewDetails = !isAppliedOrOffer && !isTechnicalTest
        sectionInterviewDetails.isVisible = showInterviewDetails

        // Show deadline section only for Technical Test
        sectionDeadline.isVisible = isTechnicalTest

        if (!showInterviewDetails && !isTechnicalTest && clearHiddenFields) {
            // Clear interview details if hiding (and not switching to technical test)
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

        if (!isTechnicalTest && clearHiddenFields) {
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
        val initialSelection = selectedDateTime
            ?.toLocalDate()?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
            ?: MaterialDatePicker.todayInUtcMilliseconds()

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
                deadlineChanged = true
                editDeadline.setText(selectedDeadline?.format(dateFormatter))
                validateForm()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        )
        dialog.show()
    }

    private fun setupValidation() {
        editCompanyName.doAfterTextChanged { validateForm() }
        editJobTitle.doAfterTextChanged { validateForm() }
        editDuration.doAfterTextChanged { validateForm() }
        validateForm() // Initial validation
    }

    private fun validateForm() {
        val hasBasicInfo = editCompanyName.text?.isNotBlank() == true &&
                editJobTitle.text?.isNotBlank() == true

        val isValid = when {
            selectedStage == InterviewStage.APPLIED ||
            selectedStage == InterviewStage.OFFER || (selectedStage == InterviewStage.UNKNOWN && !stageChanged) -> {
                hasBasicInfo
            }
            selectedStage == InterviewStage.TECHNICAL_TEST -> {
                hasBasicInfo && selectedDeadline != null
            }
            else -> {
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
        val isTechnicalTest = selectedStage == InterviewStage.TECHNICAL_TEST

        val interviewDate = selectedDateTime

        val original = originalInterview ?: return
        val deadline = if (isTechnicalTest && selectedDeadline != null) {
            originalInterview?.deadline?.takeIf { it.toLocalDate() == selectedDeadline }
                ?: selectedDeadline!!.atTime(23, 59)
        } else {
            original.deadline.takeIf { selectedStage == original.stage }
        }

        val link = if (isTechnicalTest) {
            editTestLink.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            editMeetingLink.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }

        val jobListing = editJobListing.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val updatedInterview = original.copy(
            jobTitle = editJobTitle.text.toString().trim(),
            companyName = editCompanyName.text.toString().trim(),
            stage = selectedStage,
            serverStageId = selectedStageId, serverStageName = selectedStageName,
            stageRecognized = selectedStageRecognized, stageSelectionChanged = stageChanged || original.stageSelectionChanged,
            serverMethodId = selectedMethodId, serverMethodName = selectedMethodName,
            methodSelectionChanged = methodChanged || original.methodSelectionChanged,
            durationMinutes = editDuration.text?.toString()?.trim()?.toIntOrNull(),
            method = selectedMethod,
            outcome = selectedOutcome,
            applicationDate = applicationDate,
            interviewDate = if (selectedInstant == null) interviewDate else null,
            interviewInstant = selectedInstant,
            deadline = if (deadlineChanged && isTechnicalTest) null else deadline,
            deadlineInstant = if (isTechnicalTest && deadlineChanged) selectedDeadline?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.minusSeconds(60) else original.deadlineInstant,
            interviewer = editInterviewer.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            link = link,
            jobListing = jobListing,
            notes = editNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        )

        lifecycleScope.launch {
            val company = if (updatedInterview.companyName == original.companyName) {
                original.companyId
            } else {
                repository.findOrCreateCompany(updatedInterview.companyName, original.ownerId).id
            }
            repository.update(updatedInterview.copy(companyId = company))
            finish()
        }
    }
}
