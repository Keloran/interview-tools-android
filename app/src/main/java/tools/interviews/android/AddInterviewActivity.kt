package tools.interviews.android

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AddInterviewActivity : AppCompatActivity() {

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
    private lateinit var editNotes: TextInputEditText

    private var selectedStage: InterviewStage = InterviewStage.APPLIED
    private var selectedMethod: InterviewMethod? = null
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null
    private var existingCompanies = mutableListOf<String>()

    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    companion object {
        const val EXTRA_SELECTED_DATE = "selected_date"
        const val EXTRA_INTERVIEW = "interview"
        const val EXTRA_COMPANIES = "companies"
        const val EXTRA_NEW_COMPANY = "new_company"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_interview)

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
        editClientCompany = findViewById(R.id.editClientCompany)
        editJobTitle = findViewById(R.id.editJobTitle)
        sectionInterviewDetails = findViewById(R.id.sectionInterviewDetails)
        editInterviewDate = findViewById(R.id.editInterviewDate)
        editInterviewTime = findViewById(R.id.editInterviewTime)
        dropdownMethod = findViewById(R.id.dropdownMethod)
        editInterviewer = findViewById(R.id.editInterviewer)
        editMeetingLink = findViewById(R.id.editMeetingLink)
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
            selectedDate = LocalDate.parse(dateString)
            editInterviewDate.setText(selectedDate?.format(dateFormatter))
        }
    }

    private fun setupDropdowns() {
        // Stage dropdown - default to Applied
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
        // Hide interview details for Applied and Offer stages
        val showDetails = selectedStage != InterviewStage.APPLIED &&
                         selectedStage != InterviewStage.OFFER
        sectionInterviewDetails.isVisible = showDetails

        // Clear interview details if hiding
        if (!showDetails) {
            selectedDate = null
            selectedTime = null
            selectedMethod = null
            editInterviewDate.text?.clear()
            editInterviewTime.text?.clear()
            dropdownMethod.text?.clear()
            editInterviewer.text?.clear()
            editMeetingLink.text?.clear()
        }
    }

    private fun setupDateTimePickers() {
        editInterviewDate.setOnClickListener {
            showDatePicker()
        }

        editInterviewTime.setOnClickListener {
            showTimePicker()
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

    private fun setupValidation() {
        editCompanyName.doAfterTextChanged { validateForm() }
        editJobTitle.doAfterTextChanged { validateForm() }
    }

    private fun validateForm() {
        val hasBasicInfo = editCompanyName.text?.isNotBlank() == true &&
                editJobTitle.text?.isNotBlank() == true

        val isValid = if (selectedStage == InterviewStage.APPLIED ||
                        selectedStage == InterviewStage.OFFER) {
            // For Applied/Offer, only need company and job title
            hasBasicInfo
        } else {
            // For other stages, also need interview details
            hasBasicInfo &&
                selectedDate != null &&
                selectedTime != null &&
                selectedMethod != null
        }

        buttonSave.isEnabled = isValid
    }

    private fun saveInterview() {
        val companyName = editCompanyName.text.toString().trim()
        val isNewCompany = !existingCompanies.contains(companyName)

        val interviewDate = if (selectedDate != null && selectedTime != null) {
            LocalDateTime.of(selectedDate, selectedTime)
        } else {
            null
        }

        // Set outcome based on stage
        val outcome = if (selectedStage == InterviewStage.APPLIED) {
            InterviewOutcome.AWAITING_RESPONSE
        } else {
            InterviewOutcome.SCHEDULED
        }

        val interview = Interview(
            id = System.currentTimeMillis(),
            jobTitle = editJobTitle.text.toString().trim(),
            companyName = companyName,
            clientCompany = editClientCompany.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            stage = selectedStage,
            method = selectedMethod,
            outcome = outcome,
            applicationDate = LocalDate.now(),
            interviewDate = interviewDate,
            interviewer = editInterviewer.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            link = editMeetingLink.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            notes = editNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_INTERVIEW, interview.id)
            putExtra("jobTitle", interview.jobTitle)
            putExtra("companyName", interview.companyName)
            putExtra("clientCompany", interview.clientCompany)
            putExtra("stage", interview.stage.name)
            putExtra("method", interview.method?.name)
            putExtra("outcome", interview.outcome.name)
            putExtra("applicationDate", interview.applicationDate.toString())
            putExtra("interviewDate", interview.interviewDate?.toString())
            putExtra("interviewer", interview.interviewer)
            putExtra("link", interview.link)
            putExtra("notes", interview.notes)
            if (isNewCompany) {
                putExtra(EXTRA_NEW_COMPANY, companyName)
            }
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
