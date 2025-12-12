package tools.interviews.android

import android.os.Bundle
import android.widget.CalendarView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tools.interviews.android.adapter.ListItemAdapter
import tools.interviews.android.model.ListItem
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ListItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupCalendar()
        setupRecyclerView()
        loadSampleData()
    }

    private fun setupCalendar() {
        calendarView = findViewById(R.id.calendarView)
        // Calendar features will be added later
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewItems)
        adapter = ListItemAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadSampleData() {
        val sampleItems = listOf(
            ListItem(
                id = 1,
                name = "John Smith",
                companyName = "Acme Corp",
                date = LocalDate.now(),
                label1 = "Interview",
                label2 = "Technical"
            ),
            ListItem(
                id = 2,
                name = "Jane Doe",
                companyName = "Tech Solutions",
                date = LocalDate.now().plusDays(1),
                label1 = "Meeting",
                label2 = "HR"
            ),
            ListItem(
                id = 3,
                name = "Bob Johnson",
                companyName = "StartupXYZ",
                date = LocalDate.now().plusDays(3),
                label1 = "Interview",
                label2 = "Final Round"
            )
        )
        adapter.submitList(sampleItems)
    }
}
