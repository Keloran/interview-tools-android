package tools.interviews.android

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import tools.interviews.android.data.api.*

object MessageComposer {
    fun choose(
        activity: AppCompatActivity,
        owner: String,
        context: MessageContext,
        templates: List<MessageTemplate>,
    ) {
        if (templates.isEmpty()) return
        MaterialAlertDialogBuilder(activity)
            .setTitle("Message template")
            .setItems(templates.map { it.label }.toTypedArray()) { _, position ->
                preview(activity, owner, context, templates[position])
            }
            .show()
    }

    private fun preview(
        activity: AppCompatActivity,
        owner: String,
        context: MessageContext,
        template: MessageTemplate,
    ) {
        val app = activity.application as InterviewApplication
        if (app.ownerNow() != owner) return
        val layout =
            TextInputLayout(activity).apply {
                hint = "Review and edit your message"
                setPadding(24, 16, 24, 0)
            }
        val body =
            TextInputEditText(activity).apply {
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 6
                setText(MessageTemplates.render(template, context))
            }
        layout.addView(body)
        fun open(email: Boolean) {
            if (app.ownerNow() != owner) return
            val intent =
                if (email) Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "", null))
                else Intent(Intent.ACTION_SEND).setType("text/plain")
            intent
                .putExtra(
                    Intent.EXTRA_SUBJECT,
                    "${context.jobTitle} · ${context.clientCompany ?: context.company}",
                )
                .putExtra(Intent.EXTRA_TEXT, body.text.toString())
            runCatching {
                    activity.startActivity(
                        if (email) intent else Intent.createChooser(intent, "Share message")
                    )
                }
                .onFailure {
                    Toast.makeText(
                            activity,
                            "No app is available to share this message.",
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(template.label)
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Share") { _, _ -> open(false) }
            .setNeutralButton("Email") { _, _ -> open(true) }
            .show()
    }
}
