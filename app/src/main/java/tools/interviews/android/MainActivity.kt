package tools.interviews.android

import android.os.Bundle
import android.view.View
import android.widget.CalendarView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import tools.interviews.android.adapter.InterviewAdapter
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var calendarView: CalendarView
    private lateinit var textListHeader: TextView
    private lateinit var buttonClearDate: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var fabAddInterview: FloatingActionButton
    private lateinit var adapter: InterviewAdapter

    private var allInterviews: List<Interview> = emptyList()
    private var selectedDate: LocalDate? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupToolbar()
        setupCalendar()
        setupRecyclerView()
        setupFab()
        loadSampleData()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        calendarView = findViewById(R.id.calendarView)
        textListHeader = findViewById(R.id.textListHeader)
        buttonClearDate = findViewById(R.id.buttonClearDate)
        recyclerView = findViewById(R.id.recyclerViewInterviews)
        emptyState = findViewById(R.id.emptyState)
        fabAddInterview = findViewById(R.id.fabAddInterview)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    // Settings will be implemented later
                    true
                }
                else -> false
            }
        }
    }

    private fun setupCalendar() {
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val newDate = LocalDate.of(year, month + 1, dayOfMonth)
            if (selectedDate == newDate) {
                // Tapping the same date clears the selection
                clearDateSelection()
            } else {
                selectedDate = newDate
                updateListHeader()
                filterInterviews()
            }
        }

        buttonClearDate.setOnClickListener {
            clearDateSelection()
        }
    }

    private fun clearDateSelection() {
        selectedDate = null
        updateListHeader()
        filterInterviews()
        // Reset calendar to today
        calendarView.date = System.currentTimeMillis()
    }

    private fun setupRecyclerView() {
        adapter = InterviewAdapter { interview ->
            // Interview click - will be implemented later
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFab() {
        fabAddInterview.setOnClickListener {
            // Add interview - will be implemented later
        }
    }

    private fun updateListHeader() {
        if (selectedDate != null) {
            textListHeader.text = "Interviews on ${selectedDate!!.format(dateFormatter)}"
            buttonClearDate.isVisible = true
        } else {
            textListHeader.text = "Upcoming Interviews"
            buttonClearDate.isVisible = false
        }
    }

    private fun filterInterviews() {
        val filteredList = if (selectedDate != null) {
            allInterviews.filter { interview ->
                interview.interviewDate?.toLocalDate() == selectedDate ||
                    (interview.interviewDate == null && interview.applicationDate == selectedDate)
            }
        } else {
            // Show today and future interviews
            val today = LocalDate.now()
            allInterviews.filter { interview ->
                val interviewDate = interview.interviewDate?.toLocalDate() ?: interview.applicationDate
                !interviewDate.isBefore(today)
            }.sortedBy { it.interviewDate ?: it.applicationDate.atStartOfDay() }
        }

        adapter.submitList(filteredList)
        updateEmptyState(filteredList.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        emptyState.isVisible = isEmpty
        recyclerView.isVisible = !isEmpty

        if (isEmpty) {
            val emptyTitle = findViewById<TextView>(R.id.textEmptyTitle)
            val emptyDescription = findViewById<TextView>(R.id.textEmptyDescription)

            if (selectedDate != null) {
                emptyTitle.text = "No Interviews This Day"
                emptyDescription.text = "Select another date or tap + to add an interview"
            } else {
                emptyTitle.text = "No Interviews Scheduled"
                emptyDescription.text = "Tap + to add your first interview"
            }
        }
    }

    private fun loadSampleData() {
        allInterviews = listOf(
            Interview(
                id = 1,
                jobTitle = "Senior Android Developer",
                companyName = "Acme Corp",
                clientCompany = "TechRecruit",
                stage = InterviewStage.FIRST_STAGE,
                method = InterviewMethod.VIDEO_CALL,
                outcome = InterviewOutcome.SCHEDULED,
                applicationDate = LocalDate.now().minusDays(7),
                interviewDate = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0),
                interviewer = "John Smith"
            ),
            Interview(
                id = 2,
                jobTitle = "Android Engineer",
                companyName = "Tech Solutions",
                stage = InterviewStage.PHONE_SCREEN,
                method = InterviewMethod.PHONE_CALL,
                outcome = InterviewOutcome.SCHEDULED,
                applicationDate = LocalDate.now().minusDays(5),
                interviewDate = LocalDateTime.now().plusDays(1).withHour(14).withMinute(30)
            ),
            Interview(
                id = 3,
                jobTitle = "Mobile Developer",
                companyName = "StartupXYZ",
                stage = InterviewStage.TECHNICAL_TEST,
                outcome = InterviewOutcome.AWAITING_RESPONSE,
                applicationDate = LocalDate.now().minusDays(10),
                deadline = LocalDateTime.now().plusDays(5).withHour(23).withMinute(59),
                notes = "Take-home coding challenge"
            ),
            Interview(
                id = 4,
                jobTitle = "Lead Android Developer",
                companyName = "BigTech Inc",
                stage = InterviewStage.SECOND_STAGE,
                method = InterviewMethod.IN_PERSON,
                outcome = InterviewOutcome.PASSED,
                applicationDate = LocalDate.now().minusDays(14),
                interviewDate = LocalDateTime.now().minusDays(3).withHour(11).withMinute(0)
            ),
            Interview(
                id = 5,
                jobTitle = "Software Engineer",
                companyName = "Remote Co",
                stage = InterviewStage.OFFER,
                outcome = InterviewOutcome.OFFER_RECEIVED,
                applicationDate = LocalDate.now().minusDays(21)
            )
        )
        filterInterviews()
    }
}
