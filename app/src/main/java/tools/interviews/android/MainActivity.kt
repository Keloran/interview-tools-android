package tools.interviews.android

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.CalendarView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
    private lateinit var adapter: InterviewAdapter

    private val allInterviews = mutableListOf<Interview>()
    private val allCompanies = mutableSetOf<String>()
    private var selectedDate: LocalDate? = null

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
    }

    private fun setupToolbar() {
        buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupCalendar() {
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val newDate = LocalDate.of(year, month + 1, dayOfMonth)
            if (selectedDate == newDate) {
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

    private fun updateListHeader() {
        if (selectedDate != null) {
            textListHeader.text = "Interviews on ${selectedDate!!.format(dateFormatter)}"
            buttonClearDate.isVisible = true
            fabAddInterview.show()
        } else {
            textListHeader.text = "Upcoming Interviews"
            buttonClearDate.isVisible = false
            fabAddInterview.hide()
        }
    }

    private fun filterInterviews() {
        val filteredList = if (selectedDate != null) {
            allInterviews.filter { interview ->
                interview.interviewDate?.toLocalDate() == selectedDate ||
                    (interview.interviewDate == null && interview.applicationDate == selectedDate)
            }
        } else {
            val today = LocalDate.now()
            allInterviews.filter { interview ->
                val interviewDate = interview.interviewDate?.toLocalDate() ?: interview.applicationDate
                !interviewDate.isBefore(today)
            }.sortedBy { it.interviewDate ?: it.applicationDate.atStartOfDay() }
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

            if (selectedDate != null) {
                emptyTitle.text = "No Interviews This Day"
                emptyDescription.text = "Tap + to add an interview for this date"
            } else {
                emptyTitle.text = "No Upcoming Interviews"
                emptyDescription.text = "Select a date to add an interview"
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
