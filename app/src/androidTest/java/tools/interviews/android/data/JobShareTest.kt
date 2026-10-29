package tools.interviews.android.data

import android.content.Intent
import android.widget.AutoCompleteTextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tools.interviews.android.*

@RunWith(AndroidJUnit4::class)
class JobShareTest {
    @Test fun shareTargetPrefillsAnEditableApplicationWithoutAParentOrNetworkCheck() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
            .putExtra(Intent.EXTRA_TEXT, "https://jobs.example.com/123")
            .putExtra(Intent.EXTRA_SUBJECT, "Engineer")
        assertTrue(context.packageManager.queryIntentActivities(share, 0).any { it.activityInfo.name == JobCheckActivity::class.java.name })
        val monitor = instrumentation.addMonitor(AddInterviewActivity::class.java.name, null, false)
        try {
            share.setClass(context, JobCheckActivity::class.java)
            ActivityScenario.launch<JobCheckActivity>(share).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals("https://jobs.example.com/123", activity.findViewById<TextInputEditText>(R.id.editCheckLink).text.toString())
                    assertEquals("", activity.findViewById<TextInputEditText>(R.id.editCheckDescription).text.toString())
                    assertEquals("", activity.findViewById<android.widget.TextView>(R.id.textCheckStatus).text.toString())
                    activity.findViewById<TextInputEditText>(R.id.editCheckCompany).setText("Agency")
                    activity.findViewById<TextInputEditText>(R.id.editCheckClient).setText("Employer")
                    activity.findViewById<android.view.View>(R.id.buttonAddCheckedJob).performClick()
                }
                val added = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as? AddInterviewActivity
                assertNotNull("Add application did not open", added)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals("Agency", added!!.findViewById<AutoCompleteTextView>(R.id.editCompanyName).text.toString())
                    assertEquals("Employer", added.findViewById<TextInputEditText>(R.id.editClientCompany).text.toString())
                    assertEquals("Engineer", added.findViewById<TextInputEditText>(R.id.editJobTitle).text.toString())
                    assertEquals("https://jobs.example.com/123", added.findViewById<TextInputEditText>(R.id.editJobListing).text.toString())
                    assertEquals("Applied", added.findViewById<AutoCompleteTextView>(R.id.dropdownStage).text.toString())
                    assertFalse(added.intent.getBooleanExtra(AddInterviewActivity.EXTRA_NEXT_STAGE_MODE, false))
                    added.finish()
                }
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
