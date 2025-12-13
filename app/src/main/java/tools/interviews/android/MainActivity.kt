package tools.interviews.android

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import tools.interviews.android.adapter.InterviewAdapter
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
    private lateinit var editSearchCompany: EditText
    private lateinit var buttonClearSearch: ImageButton
    private lateinit var adapter: InterviewAdapter

    private val allInterviews = mutableListOf<Interview>()
    private val allCompanies = mutableSetOf<String>()
    private var selectedDate: LocalDate? = null
    private var companyFilter: String? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    private val addInterviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                // Handle new company
                data.getStringExtra(AddInterviewActivity.EXTRA_NEW_COMPANY)?.let { newCompany ->
                    allCompanies.add(newCompany)
                }

                val interview = parseInterviewFromIntent(data)
                interview?.let {
                    allInterviews.add(it)
                    filterInterviews()
                    Snackbar.make(recyclerView, "Interview added", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupToolbar()
        setupCalendar()
        setupRecyclerView()
        setupSwipeActions()
        setupFab()
        setupSearch()
        loadData()
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
        adapter = InterviewAdapter { interview ->
            // Interview click - will be implemented later
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSwipeActions() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val interview = adapter.currentList[position]

                when (direction) {
                    ItemTouchHelper.RIGHT -> {
                        // Awaiting Feedback (primary right swipe action)
                        updateInterviewOutcome(interview, InterviewOutcome.AWAITING_RESPONSE)
                        Snackbar.make(recyclerView, "Status: Awaiting Feedback", Snackbar.LENGTH_SHORT).show()
                    }
                    ItemTouchHelper.LEFT -> {
                        // Rejected
                        updateInterviewOutcome(interview, InterviewOutcome.REJECTED)
                        Snackbar.make(recyclerView, "Status: Rejected", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = Paint()
                val cornerRadius = 8f * resources.displayMetrics.density

                if (dX > 0) {
                    // Swiping right - Awaiting Feedback (yellow/purple)
                    paint.color = ContextCompat.getColor(this@MainActivity, R.color.outcome_awaiting)
                    val background = RectF(
                        itemView.left.toFloat(),
                        itemView.top.toFloat(),
                        itemView.left + dX,
                        itemView.bottom.toFloat()
                    )
                    c.drawRoundRect(background, cornerRadius, cornerRadius, paint)

                    // Draw icon and text
                    val icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_schedule)
                    icon?.let {
                        val iconMargin = 16 * resources.displayMetrics.density
                        val iconSize = 24 * resources.displayMetrics.density
                        val iconTop = itemView.top + (itemView.height - iconSize) / 2
                        it.setBounds(
                            (itemView.left + iconMargin).toInt(),
                            iconTop.toInt(),
                            (itemView.left + iconMargin + iconSize).toInt(),
                            (iconTop + iconSize).toInt()
                        )
                        it.setTint(ContextCompat.getColor(this@MainActivity, R.color.black))
                        it.draw(c)
                    }

                    // Draw text
                    val textPaint = Paint().apply {
                        color = ContextCompat.getColor(this@MainActivity, R.color.black)
                        textSize = 14 * resources.displayMetrics.density
                        isAntiAlias = true
                    }
                    val text = "Awaiting"
                    val textX = itemView.left + 56 * resources.displayMetrics.density
                    val textY = itemView.top + itemView.height / 2 + textPaint.textSize / 3
                    if (dX > 120 * resources.displayMetrics.density) {
                        c.drawText(text, textX, textY, textPaint)
                    }

                } else if (dX < 0) {
                    // Swiping left - Rejected (red)
                    paint.color = ContextCompat.getColor(this@MainActivity, R.color.outcome_rejected)
                    val background = RectF(
                        itemView.right + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat()
                    )
                    c.drawRoundRect(background, cornerRadius, cornerRadius, paint)

                    // Draw icon
                    val icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_close)
                    icon?.let {
                        val iconMargin = 16 * resources.displayMetrics.density
                        val iconSize = 24 * resources.displayMetrics.density
                        val iconTop = itemView.top + (itemView.height - iconSize) / 2
                        it.setBounds(
                            (itemView.right - iconMargin - iconSize).toInt(),
                            iconTop.toInt(),
                            (itemView.right - iconMargin).toInt(),
                            (iconTop + iconSize).toInt()
                        )
                        it.setTint(ContextCompat.getColor(this@MainActivity, R.color.white))
                        it.draw(c)
                    }

                    // Draw text
                    val textPaint = Paint().apply {
                        color = ContextCompat.getColor(this@MainActivity, R.color.white)
                        textSize = 14 * resources.displayMetrics.density
                        isAntiAlias = true
                    }
                    val text = "Rejected"
                    val textWidth = textPaint.measureText(text)
                    val textX = itemView.right - 56 * resources.displayMetrics.density - textWidth
                    val textY = itemView.top + itemView.height / 2 + textPaint.textSize / 3
                    if (-dX > 120 * resources.displayMetrics.density) {
                        c.drawText(text, textX, textY, textPaint)
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun updateInterviewOutcome(interview: Interview, newOutcome: InterviewOutcome) {
        val index = allInterviews.indexOfFirst { it.id == interview.id }
        if (index != -1) {
            allInterviews[index] = allInterviews[index].copy(outcome = newOutcome)
            filterInterviews()
        }
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
        // Handle text changes to show/hide clear button
        editSearchCompany.doAfterTextChanged { text ->
            buttonClearSearch.isVisible = !text.isNullOrEmpty()
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
                id = data.getLongExtra(AddInterviewActivity.EXTRA_INTERVIEW, System.currentTimeMillis()),
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
                notes = data.getStringExtra("notes")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun loadData() {
        // Data will be loaded from local storage/server later
        filterInterviews()
    }
}
