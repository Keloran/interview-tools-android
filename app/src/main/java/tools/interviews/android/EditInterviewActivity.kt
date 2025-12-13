package tools.interviews.android

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class EditInterviewActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var buttonSave: MaterialButton
    private lateinit var dropdownStage: AutoCompleteTextView
    private lateinit var editCompanyName: AutoCompleteTextView
    private lateinit var editClientCompany: TextInputEditText
    private lateinit var editJobTitle: TextInputEditText
    private lateinit var sectionInterviewDetails: LinearLayout
    private lateinit var editInterviewDate: TextInputEditText
    private lateinit var editInterviewTime: TextInputEditText
    private lateinit var dropdownMethod: AutoCompleteTextView
    private lateinit var editInterviewer: TextInputEditText
    private lateinit var editMeetingLink: TextInputEditText
    private lateinit var sectionDeadline: LinearLayout
    private lateinit var editDeadline: TextInputEditText
    private lateinit var editTestLink: TextInputEditText
    private lateinit var editNotes: TextInputEditText

    private lateinit var repository: InterviewRepository
    private var interviewId: Long = -1
    private var originalInterview: Interview? = null

    private var selectedStage: InterviewStage = InterviewStage.APPLIED
    private var selectedMethod: InterviewMethod? = null
    private var selectedOutcome: InterviewOutcome = InterviewOutcome.SCHEDULED
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null
    private var selectedDeadline: LocalDate? = null
    private var applicationDate: LocalDate = LocalDate.now()
    private var existingCompanies = mutableListOf<String>()

    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    companion object {
        const val EXTRA_INTERVIEW_ID = "interview_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_interview)

        repository = (application as InterviewApplication).repository
        interviewId = intent.getLongExtra(EXTRA_INTERVIEW_ID, -1)

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
        editClientCompany = findViewById(R.id.editClientCompany)
        editJobTitle = findViewById(R.id.editJobTitle)
        sectionInterviewDetails = findViewById(R.id.sectionInterviewDetails)
        editInterviewDate = findViewById(R.id.editInterviewDate)
        editInterviewTime = findViewById(R.id.editInterviewTime)
        dropdownMethod = findViewById(R.id.dropdownMethod)
        editInterviewer = findViewById(R.id.editInterviewer)
        editMeetingLink = findViewById(R.id.editMeetingLink)
        sectionDeadline = findViewById(R.id.sectionDeadline)
        editDeadline = findViewById(R.id.editDeadline)
        editTestLink = findViewById(R.id.editTestLink)
        editNotes = findViewById(R.id.editNotes)

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
            existingCompanies.addAll(repository.allCompanies.first())
            setupCompanyAutocomplete()

            // Load the interview
            originalInterview = repository.getById(interviewId)
            originalInterview?.let { interview ->
                populateForm(interview)
                setupDropdowns()
                setupDateTimePickers()
                setupValidation()
                updateInterviewDetailsVisibility()
            } ?: finish()
        }
    }

    private fun populateForm(interview: Interview) {
        selectedStage = interview.stage
        selectedMethod = interview.method
        selectedOutcome = interview.outcome
        applicationDate = interview.applicationDate

        editCompanyName.setText(interview.companyName)
        editClientCompany.setText(interview.clientCompany ?: "")
        editJobTitle.setText(interview.jobTitle)

        interview.interviewDate?.let {
            selectedDate = it.toLocalDate()
            selectedTime = it.toLocalTime()
            editInterviewDate.setText(selectedDate?.format(dateFormatter))
            editInterviewTime.setText(selectedTime?.format(timeFormatter))
        }

        interview.deadline?.let {
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
    }

    private fun setupDropdowns() {
        // Stage dropdown
        val stages = InterviewStage.entries.map { it.displayName }
        val stageAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, stages)
        dropdownStage.setAdapter(stageAdapter)
        dropdownStage.setText(selectedStage.displayName, false)
        dropdownStage.setOnItemClickListener { _, _, position, _ ->
            selectedStage = InterviewStage.entries[position]
            updateInterviewDetailsVisibility()
            validateForm()
        }

        // Method dropdown
        val methods = InterviewMethod.entries.map { it.displayName }
        val methodAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, methods)
        dropdownMethod.setAdapter(methodAdapter)
        selectedMethod?.let {
            dropdownMethod.setText(it.displayName, false)
        }
        dropdownMethod.setOnItemClickListener { _, _, position, _ ->
            selectedMethod = InterviewMethod.entries[position]
            validateForm()
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

        // Show interview details for normal interview stages
        val showInterviewDetails = !isAppliedOrOffer && !isTechnicalTest
        sectionInterviewDetails.isVisible = showInterviewDetails

        // Show deadline section only for Technical Test
        sectionDeadline.isVisible = isTechnicalTest

        if (!showInterviewDetails && !isTechnicalTest) {
            // Clear interview details if hiding (and not switching to technical test)
            selectedDate = null
            selectedTime = null
            selectedMethod = null
            editInterviewDate.text?.clear()
            editInterviewTime.text?.clear()
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
        editInterviewDate.setOnClickListener {
            showDatePicker()
        }

        editInterviewTime.setOnClickListener {
            showTimePicker()
        }

        editDeadline.setOnClickListener {
            showDeadlinePicker()
        }
    }

    private fun showDatePicker() {
        val initialDate = selectedDate ?: LocalDate.now()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                editInterviewDate.setText(selectedDate?.format(dateFormatter))
                validateForm()
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        )
        dialog.show()
    }

    private fun showTimePicker() {
        val initialTime = selectedTime ?: LocalTime.of(9, 0)
        val dialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedTime = LocalTime.of(hourOfDay, minute)
                editInterviewTime.setText(selectedTime?.format(timeFormatter))
                validateForm()
            },
            initialTime.hour,
            initialTime.minute,
            false
        )
        dialog.show()
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
        dialog.show()
    }

    private fun setupValidation() {
        editCompanyName.doAfterTextChanged { validateForm() }
        editJobTitle.doAfterTextChanged { validateForm() }
        validateForm() // Initial validation
    }

    private fun validateForm() {
        val hasBasicInfo = editCompanyName.text?.isNotBlank() == true &&
                editJobTitle.text?.isNotBlank() == true

        val isValid = when {
            selectedStage == InterviewStage.APPLIED ||
            selectedStage == InterviewStage.OFFER -> {
                hasBasicInfo
            }
            selectedStage == InterviewStage.TECHNICAL_TEST -> {
                hasBasicInfo && selectedDeadline != null
            }
            else -> {
                hasBasicInfo &&
                    selectedDate != null &&
                    selectedTime != null &&
                    selectedMethod != null
            }
        }

        buttonSave.isEnabled = isValid
    }

    private fun saveInterview() {
        val isTechnicalTest = selectedStage == InterviewStage.TECHNICAL_TEST

        val interviewDate = if (selectedDate != null && selectedTime != null) {
            LocalDateTime.of(selectedDate, selectedTime)
        } else {
            null
        }

        val deadline = if (isTechnicalTest && selectedDeadline != null) {
            selectedDeadline!!.atTime(23, 59)
        } else {
            null
        }

        val link = if (isTechnicalTest) {
            editTestLink.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            editMeetingLink.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }

        val updatedInterview = Interview(
            id = interviewId,
            jobTitle = editJobTitle.text.toString().trim(),
            companyName = editCompanyName.text.toString().trim(),
            clientCompany = editClientCompany.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            stage = selectedStage,
            method = selectedMethod,
            outcome = selectedOutcome,
            applicationDate = applicationDate,
            interviewDate = interviewDate,
            deadline = deadline,
            interviewer = editInterviewer.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            link = link,
            notes = editNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        )

        lifecycleScope.launch {
            repository.update(updatedInterview)
            finish()
        }
    }
}
