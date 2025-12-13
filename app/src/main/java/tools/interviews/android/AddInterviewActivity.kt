package tools.interviews.android

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var editCompanyName: TextInputEditText
    private lateinit var editClientCompany: TextInputEditText
    private lateinit var editJobTitle: TextInputEditText
    private lateinit var editInterviewDate: TextInputEditText
    private lateinit var editInterviewTime: TextInputEditText
    private lateinit var dropdownMethod: AutoCompleteTextView
    private lateinit var editInterviewer: TextInputEditText
    private lateinit var editMeetingLink: TextInputEditText
    private lateinit var editNotes: TextInputEditText

    private var selectedStage: InterviewStage = InterviewStage.FIRST_STAGE
    private var selectedMethod: InterviewMethod? = null
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    companion object {
        const val EXTRA_SELECTED_DATE = "selected_date"
        const val EXTRA_INTERVIEW = "interview"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_interview)

        setupViews()
        setupToolbar()
        setupDropdowns()
        setupDateTimePickers()
        setupValidation()
        loadIntentData()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        buttonSave = findViewById(R.id.buttonSave)
        dropdownStage = findViewById(R.id.dropdownStage)
        editCompanyName = findViewById(R.id.editCompanyName)
        editClientCompany = findViewById(R.id.editClientCompany)
        editJobTitle = findViewById(R.id.editJobTitle)
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

    private fun setupDropdowns() {
        // Stage dropdown
        val stages = InterviewStage.entries.map { it.displayName }
        val stageAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, stages)
        dropdownStage.setAdapter(stageAdapter)
        dropdownStage.setText(selectedStage.displayName, false)
        dropdownStage.setOnItemClickListener { _, _, position, _ ->
            selectedStage = InterviewStage.entries[position]
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
        val isValid = editCompanyName.text?.isNotBlank() == true &&
                editJobTitle.text?.isNotBlank() == true &&
                selectedDate != null &&
                selectedTime != null &&
                selectedMethod != null

        buttonSave.isEnabled = isValid
    }

    private fun loadIntentData() {
        // Pre-fill date if passed from main activity
        intent.getStringExtra(EXTRA_SELECTED_DATE)?.let { dateString ->
            selectedDate = LocalDate.parse(dateString)
            editInterviewDate.setText(selectedDate?.format(dateFormatter))
        }
    }

    private fun saveInterview() {
        val interview = Interview(
            id = System.currentTimeMillis(),
            jobTitle = editJobTitle.text.toString().trim(),
            companyName = editCompanyName.text.toString().trim(),
            clientCompany = editClientCompany.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            stage = selectedStage,
            method = selectedMethod,
            outcome = InterviewOutcome.SCHEDULED,
            applicationDate = LocalDate.now(),
            interviewDate = LocalDateTime.of(selectedDate, selectedTime),
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
            putExtra("interviewDate", interview.interviewDate.toString())
            putExtra("interviewer", interview.interviewer)
            putExtra("link", interview.link)
            putExtra("notes", interview.notes)
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
