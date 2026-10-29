package tools.interviews.android

import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.data.api.*

class RecruiterForm(private val activity: AppCompatActivity) {
    private val toggle = activity.findViewById<MaterialSwitch>(R.id.switchViaRecruiter)
    private val fields = activity.findViewById<LinearLayout>(R.id.recruiterFields)
    private val name = activity.findViewById<TextInputEditText>(R.id.editRecruiterName)
    private val email = activity.findViewById<TextInputEditText>(R.id.editRecruiterEmail)
    private val linkedIn = activity.findViewById<TextInputEditText>(R.id.editRecruiterLinkedIn)
    private val company = activity.findViewById<AutoCompleteTextView>(R.id.editCompanyName)
    private val status = activity.findViewById<TextView>(R.id.textAgencyRecord)
    private var original: String? = null
    private var populating = false
    var changed = false
        private set
    private var records: List<RecruiterRecord> = emptyList()
    init {
        toggle.setOnCheckedChangeListener { _, enabled -> fields.isVisible = enabled; if (!populating) changed = true }
        for (field in listOf(name,email,linkedIn)) field.doAfterTextChanged { if (!populating) changed = true }
        company.doAfterTextChanged { showRecord() }
        val app = activity.application as InterviewApplication
        val owner = app.ownerNow()
        if (owner != AccountOwners.GUEST) activity.lifecycleScope.launch {
            try {
                val response = app.authenticatedRequests.run(owner) { APIService.getInstance().recruiters() }
                records = RecruiterRecord.parse(response)
                showRecord()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status.text = "Agency record unavailable. Your contact edits can still be saved offline." }
        }
    }
    fun bind(json: String?) {
        populating = true; original = json
        val contact = RecruiterContact.parse(json)
        toggle.isChecked = contact.viaRecruiter
        fields.isVisible = contact.viaRecruiter
        name.setText(contact.name); email.setText(contact.email); linkedIn.setText(contact.linkedIn)
        changed = false; populating = false
    }
    fun json(): String? = if (!changed) original else RecruiterContact(toggle.isChecked, name.text.toString().trim(), email.text.toString().trim(), linkedIn.text.toString().trim()).json()
    fun showError(error: IllegalArgumentException) { status.text = error.message; status.requestFocus() }
    private fun showRecord() {
        val record = records.firstOrNull { it.contact == null && it.agency.trim().equals(company.text.toString().trim(), true) }
        status.text = record?.summary.orEmpty()
    }
}
