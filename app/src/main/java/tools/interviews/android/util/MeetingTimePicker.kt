package tools.interviews.android.util

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tools.interviews.android.model.InterviewTimes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun AppCompatActivity.confirmMeetingTime(local: LocalDateTime, onConfirmed: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val choices = InterviewTimes.validInstants(local, zone)
    when (choices.size) {
        0 -> MaterialAlertDialogBuilder(this).setTitle("Choose another time")
            .setMessage("$local does not exist in $zone because the clocks move forward.")
            .setPositiveButton(android.R.string.ok, null).show()
        1 -> onConfirmed(choices.single())
        else -> MaterialAlertDialogBuilder(this).setTitle("Repeated time in $zone")
            .setItems(choices.map { "${it.atZone(zone).offset} · $it" }.toTypedArray()) { _, index -> onConfirmed(choices[index]) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}
