package tools.interviews.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CalendarView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tools.interviews.android.adapter.InterviewAdapter
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var buttonSettings: ImageButton
    private lateinit var calendarView: CalendarView
    private lateinit var textListHeader: TextView
    private lateinit var buttonClearDate: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var fabAddInterview: FloatingActionButton
    private lateinit var editSearchCompany: AutoCompleteTextView
    private lateinit var buttonClearSearch: ImageButton
    private lateinit var adapter: InterviewAdapter
    private lateinit var companySearchAdapter: ArrayAdapter<String>

    private lateinit var repository: InterviewRepository
    private var allInterviews = listOf<Interview>()
    private var allCompanies = listOf<String>()
    private var selectedDate: LocalDate? = null
    private var companyFilter: String? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    private val addInterviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val interview = parseInterviewFromIntent(data)
                interview?.let {
                    lifecycleScope.launch {
                        repository.insert(it)
                        Snackbar.make(recyclerView, "Interview added", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = (application as InterviewApplication).repository

        setupViews()
        setupToolbar()
        setupCalendar()
        setupRecyclerView()
        setupFab()
        setupSearch()
        observeData()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        buttonSettings = findViewById(R.id.buttonSettings)
        calendarView = findViewById(R.id.calendarView)
        textListHeader = findViewById(R.id.textListHeader)
        buttonClearDate = findViewById(R.id.buttonClearDate)
        recyclerView = findViewById(R.id.recyclerViewInterviews)
        emptyState = findViewById(R.id.emptyState)
        fabAddInterview = findViewById(R.id.fabAddInterview)
        editSearchCompany = findViewById(R.id.editSearchCompany)
        buttonClearSearch = findViewById(R.id.buttonClearSearch)
    }

    private fun setupToolbar() {
        buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupCalendar() {
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val newDate = LocalDate.of(year, month + 1, dayOfMonth)
            // Clear company filter when selecting a date
            if (companyFilter != null) {
                companyFilter = null
                editSearchCompany.text?.clear()
            }
            if (selectedDate == newDate) {
                clearDateSelection()
            } else {
                selectedDate = newDate
                updateListHeader()
                filterInterviews()
            }
        }

        buttonClearDate.setOnClickListener {
            if (companyFilter != null) {
                clearCompanyFilter()
            } else {
                clearDateSelection()
            }
        }
    }

    private fun clearDateSelection() {
        selectedDate = null
        updateListHeader()
        filterInterviews()
        calendarView.date = System.currentTimeMillis()
    }

    private fun setupRecyclerView() {
        adapter = InterviewAdapter(
            onItemClick = { interview ->
                val intent = Intent(this, InterviewDetailActivity::class.java)
                intent.putExtra(InterviewDetailActivity.EXTRA_INTERVIEW_ID, interview.id)
                startActivity(intent)
            },
            onAwaitingClick = { interview ->
                updateInterviewOutcome(interview, InterviewOutcome.AWAITING_RESPONSE)
                Snackbar.make(recyclerView, "Status: Awaiting Feedback", Snackbar.LENGTH_SHORT).show()
            },
            onNextStageClick = { interview ->
                launchNextStage(interview)
            },
            onRejectClick = { interview ->
                updateInterviewOutcome(interview, InterviewOutcome.REJECTED)
                Snackbar.make(recyclerView, "Status: Rejected", Snackbar.LENGTH_SHORT).show()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Close any open swipe when scrolling
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    adapter.closeAllItems()
                }
            }
        })
    }

    private fun updateInterviewOutcome(interview: Interview, newOutcome: InterviewOutcome) {
        lifecycleScope.launch {
            val updatedInterview = interview.copy(outcome = newOutcome)
            repository.update(updatedInterview)
        }
    }

    private fun launchNextStage(interview: Interview) {
        val intent = Intent(this, AddInterviewActivity::class.java).apply {
            putExtra(AddInterviewActivity.EXTRA_NEXT_STAGE_MODE, true)
            putExtra(AddInterviewActivity.EXTRA_COMPANY_NAME, interview.companyName)
            putExtra(AddInterviewActivity.EXTRA_CLIENT_COMPANY, interview.clientCompany)
            putExtra(AddInterviewActivity.EXTRA_JOB_TITLE, interview.jobTitle)
            selectedDate?.let {
                putExtra(AddInterviewActivity.EXTRA_SELECTED_DATE, it.toString())
            }
            putStringArrayListExtra(AddInterviewActivity.EXTRA_COMPANIES, ArrayList(allCompanies))
        }
        addInterviewLauncher.launch(intent)
    }

    private fun setupFab() {
        fabAddInterview.setOnClickListener {
            val intent = Intent(this, AddInterviewActivity::class.java)
            selectedDate?.let {
                intent.putExtra(AddInterviewActivity.EXTRA_SELECTED_DATE, it.toString())
            }
            intent.putStringArrayListExtra(
                AddInterviewActivity.EXTRA_COMPANIES,
                ArrayList(allCompanies)
            )
            addInterviewLauncher.launch(intent)
        }
    }

    private fun setupSearch() {
        // Set up autocomplete adapter
        companySearchAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf<String>()
        )
        editSearchCompany.setAdapter(companySearchAdapter)

        // Handle text changes to show/hide clear button
        editSearchCompany.doAfterTextChanged { text ->
            buttonClearSearch.isVisible = !text.isNullOrEmpty()
        }

        // Handle item selection from autocomplete dropdown
        editSearchCompany.setOnItemClickListener { _, _, position, _ ->
            val selectedCompany = companySearchAdapter.getItem(position)
            if (selectedCompany != null) {
                setCompanyFilter(selectedCompany)
            }
        }

        // Handle search action (keyboard search button)
        editSearchCompany.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = editSearchCompany.text.toString().trim()
                if (query.isNotEmpty()) {
                    setCompanyFilter(query)
                }
                true
            } else {
                false
            }
        }

        // Clear search button
        buttonClearSearch.setOnClickListener {
            clearCompanyFilter()
        }
    }

    private fun observeData() {
        // Observe interviews from database
        lifecycleScope.launch {
            repository.allInterviews.collectLatest { interviews ->
                allInterviews = interviews
                filterInterviews()
            }
        }

        // Observe companies from database
        lifecycleScope.launch {
            repository.allCompanies.collectLatest { companies ->
                allCompanies = companies
                updateCompanySearchAdapter()
            }
        }
    }

    private fun updateCompanySearchAdapter() {
        companySearchAdapter.clear()
        companySearchAdapter.addAll(allCompanies.sorted())
    }

    private fun setCompanyFilter(query: String) {
        companyFilter = query
        // Clear date filter when searching for company
        selectedDate = null
        calendarView.date = System.currentTimeMillis()
        // Hide keyboard
        hideKeyboard()
        updateListHeader()
        filterInterviews()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editSearchCompany.windowToken, 0)
        editSearchCompany.clearFocus()
    }

    private fun clearCompanyFilter() {
        companyFilter = null
        editSearchCompany.text?.clear()
        editSearchCompany.clearFocus()
        updateListHeader()
        filterInterviews()
    }

    private fun updateListHeader() {
        when {
            companyFilter != null -> {
                textListHeader.text = "Interviews at \"$companyFilter\""
                buttonClearDate.isVisible = true
                buttonClearDate.text = "Clear Filter"
                fabAddInterview.hide()
            }
            selectedDate != null -> {
                textListHeader.text = "Interviews on ${selectedDate!!.format(dateFormatter)}"
                buttonClearDate.isVisible = true
                buttonClearDate.text = "Clear"
                fabAddInterview.show()
            }
            else -> {
                textListHeader.text = "Upcoming Interviews"
                buttonClearDate.isVisible = false
                buttonClearDate.text = "Clear"
                fabAddInterview.hide()
            }
        }
    }

    private fun filterInterviews() {
        val filteredList = when {
            // Company filter - show ALL interviews with matching company (past and future)
            companyFilter != null -> {
                allInterviews.filter { interview ->
                    interview.companyName.contains(companyFilter!!, ignoreCase = true) ||
                        interview.clientCompany?.contains(companyFilter!!, ignoreCase = true) == true
                }.sortedByDescending {
                    it.interviewDate ?: it.deadline ?: it.applicationDate.atStartOfDay()
                }
            }
            // Date filter
            selectedDate != null -> {
                allInterviews.filter { interview ->
                    val relevantDate = interview.interviewDate?.toLocalDate()
                        ?: interview.deadline?.toLocalDate()
                        ?: interview.applicationDate
                    relevantDate == selectedDate
                }
            }
            // Default - upcoming interviews only
            else -> {
                val today = LocalDate.now()
                allInterviews.filter { interview ->
                    val relevantDate = interview.interviewDate?.toLocalDate()
                        ?: interview.deadline?.toLocalDate()
                        ?: interview.applicationDate
                    !relevantDate.isBefore(today)
                }.sortedBy {
                    it.interviewDate ?: it.deadline ?: it.applicationDate.atStartOfDay()
                }
            }
        }

        adapter.submitList(filteredList.toList())
        updateEmptyState(filteredList.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        emptyState.isVisible = isEmpty
        recyclerView.isVisible = !isEmpty

        if (isEmpty) {
            val emptyTitle = findViewById<TextView>(R.id.textEmptyTitle)
            val emptyDescription = findViewById<TextView>(R.id.textEmptyDescription)

            when {
                companyFilter != null -> {
                    emptyTitle.text = "No Interviews Found"
                    emptyDescription.text = "No interviews with \"$companyFilter\""
                }
                selectedDate != null -> {
                    emptyTitle.text = "No Interviews This Day"
                    emptyDescription.text = "Tap + to add an interview for this date"
                }
                else -> {
                    emptyTitle.text = "No Upcoming Interviews"
                    emptyDescription.text = "Select a date to add an interview"
                }
            }
        }
    }

    private fun parseInterviewFromIntent(data: Intent): Interview? {
        return try {
            Interview(
                id = 0, // Let Room auto-generate the ID
                jobTitle = data.getStringExtra("jobTitle") ?: return null,
                companyName = data.getStringExtra("companyName") ?: return null,
                clientCompany = data.getStringExtra("clientCompany"),
                stage = InterviewStage.valueOf(data.getStringExtra("stage") ?: "FIRST_STAGE"),
                method = data.getStringExtra("method")?.let { InterviewMethod.valueOf(it) },
                outcome = InterviewOutcome.valueOf(data.getStringExtra("outcome") ?: "SCHEDULED"),
                applicationDate = LocalDate.parse(data.getStringExtra("applicationDate")),
                interviewDate = data.getStringExtra("interviewDate")?.let { LocalDateTime.parse(it) },
                deadline = data.getStringExtra("deadline")?.let { LocalDateTime.parse(it) },
                interviewer = data.getStringExtra("interviewer"),
                link = data.getStringExtra("link"),
                jobListing = data.getStringExtra("jobListing"),
                notes = data.getStringExtra("notes")
            )
        } catch (e: Exception) {
            null
        }
    }
}
