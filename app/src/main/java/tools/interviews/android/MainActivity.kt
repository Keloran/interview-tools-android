package tools.interviews.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tools.interviews.android.adapter.InterviewAdapter
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.data.api.APIService
import tools.interviews.android.data.api.SyncService
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewOutcome
import java.time.LocalDate
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
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: InterviewAdapter
    private lateinit var companySearchAdapter: ArrayAdapter<String>

    private lateinit var repository: InterviewRepository
    private lateinit var syncService: SyncService
    private var allInterviews = listOf<Interview>()
    private var allCompanies = listOf<String>()
    private var selectedDate: LocalDate? = null
    private var companyFilter: String? = null
    private var hasSyncedThisSession = false

    private lateinit var appUpdateManager: AppUpdateManager

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.w(TAG, "Update flow failed with result code: ${result.resultCode}")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    private val addInterviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Interview is now saved directly in AddInterviewActivity
            // The list will update automatically via Flow observation
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as InterviewApplication
        repository = app.repository
        syncService = app.syncService

        setupViews()
        setupToolbar()
        setupCalendar()
        setupRecyclerView()
        setupFab()
        setupSearch()
        observeData()
        observeAuthAndSync()

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        // Re-sync when returning to the app (e.g., after sign-in from settings)
        if (!hasSyncedThisSession) {
            checkAndSync()
        }
    }

    private fun observeAuthAndSync() {
        // Observe user auth state and sync when signed in
        lifecycleScope.launch {
            Clerk.userFlow.collect { user ->
                // Enable pull-to-refresh only when signed in
                swipeRefreshLayout.isEnabled = user != null

                if (user != null && !hasSyncedThisSession) {
                    Log.d(TAG, "User signed in, triggering sync...")
                    performSync()
                }
            }
        }
    }

    private fun checkAndSync() {
        lifecycleScope.launch {
            val user = Clerk.userFlow.value
            if (user != null) {
                performSync()
            }
        }
    }

    private fun performSync() {
        lifecycleScope.launch {
            try {
                // Get the session token from Clerk
                val session = Clerk.sessionFlow.value
                val token = session?.fetchToken()?.successOrNull()

                if (token != null) {
                    Log.d(TAG, "Got auth token, starting sync...")
                    APIService.getInstance().setAuthToken(token.jwt)
                    syncService.syncAll()
                    hasSyncedThisSession = true
                    Log.d(TAG, "Sync completed")
                } else {
                    Log.w(TAG, "No auth token available, skipping sync")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed: ${e.message}", e)
                Snackbar.make(recyclerView, "Sync failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
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
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        // Setup pull-to-refresh
        swipeRefreshLayout.setOnRefreshListener {
            performPullToRefreshSync()
        }
    }

    private fun performPullToRefreshSync() {
        lifecycleScope.launch {
            try {
                val session = Clerk.sessionFlow.value
                val token = session?.fetchToken()?.successOrNull()

                if (token != null) {
                    Log.d(TAG, "Pull-to-refresh: starting sync...")
                    APIService.getInstance().setAuthToken(token.jwt)
                    syncService.syncAll()
                    Log.d(TAG, "Pull-to-refresh: sync completed")
                    Snackbar.make(recyclerView, "Sync complete", Snackbar.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Pull-to-refresh: no auth token available")
                    Snackbar.make(recyclerView, "Unable to sync - please sign in again", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pull-to-refresh sync failed: ${e.message}", e)
                Snackbar.make(recyclerView, "Sync failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
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
                    // Local update already succeeded, so don't show error to user
                }
            }
        }
    }

    private fun launchNextStage(interview: Interview) {
        val intent = Intent(this, AddInterviewActivity::class.java).apply {
            putExtra(AddInterviewActivity.EXTRA_NEXT_STAGE_MODE, true)
            putExtra(AddInterviewActivity.EXTRA_PREVIOUS_INTERVIEW_ID, interview.id)
            putExtra(AddInterviewActivity.EXTRA_COMPANY_NAME, interview.companyName)
            putExtra(AddInterviewActivity.EXTRA_CLIENT_COMPANY, interview.clientCompany)
            putExtra(AddInterviewActivity.EXTRA_JOB_TITLE, interview.jobTitle)
            putExtra(AddInterviewActivity.EXTRA_JOB_LISTING, interview.jobListing)
            putExtra(AddInterviewActivity.EXTRA_APPLICATION_DATE, interview.applicationDate.toString())
            putExtra(AddInterviewActivity.EXTRA_NOTES, interview.notes)
            putExtra(AddInterviewActivity.EXTRA_METADATA_JSON, interview.metadataJSON)
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

    private fun checkForUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(
                    AppUpdateType.IMMEDIATE
                )
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
                )
            }
        }
    }
}
