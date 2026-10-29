package tools.interviews.android.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.clerk.api.Clerk
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tools.interviews.android.FollowUpsActivity
import tools.interviews.android.InterviewApplication
import tools.interviews.android.R
import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.data.api.APIService

object FollowUpNotificationPolicy {
    fun shouldNotify(dueAt: String, state: JsonObject?, lastNotified: String?): Boolean =
        state?.get("pending")?.asBoolean != true &&
            state?.get("dueAt")?.asString != dueAt &&
            lastNotified != dueAt
}

object FollowUpReminders {
    const val TAG = "account-follow-up-reminders"

    fun schedule(context: Context, owner: String) {
        if (owner == AccountOwners.GUEST || !DebriefReminders.allowed(context)) return
        val work =
            PeriodicWorkRequestBuilder<FollowUpReminderWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(workDataOf("owner" to owner))
                .addTag(TAG)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("follow-ups:$owner", ExistingPeriodicWorkPolicy.UPDATE, work)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    fun post(context: Context, owner: String, id: Int): Boolean {
        if (!DebriefReminders.allowed(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "follow-ups",
                "Follow-up reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val intent =
            Intent(context, FollowUpsActivity::class.java).putExtra("expected_owner", owner)
        val code = "$owner:$id".hashCode()
        val pending =
            PendingIntent.getActivity(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, "follow-ups")
                .setSmallIcon(R.drawable.ic_calendar_empty)
                .setContentTitle("A follow-up is due")
                .setContentText("Review your application and promised reply date.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        return try {
            manager.notify(TAG, code, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

class FollowUpReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        if (owner == AccountOwners.GUEST || !DebriefReminders.allowed(applicationContext))
            return Result.success()
        if (withTimeoutOrNull(15000) { Clerk.isInitialized.first { it } } != true)
            return Result.retry()
        val app = applicationContext as InterviewApplication
        if (app.ownerNow() != owner) return Result.success()
        return try {
            app.followUpQueue.push(owner)
            val due = app.authenticatedRequests.run(owner) { APIService.getInstance().followUps() }
            val states = app.followUpQueue.states(owner)
            for (element in due) {
                val record = element.asJsonObject
                val id = record.get("id").asInt
                val dueAt = record.get("dueAt").asString
                val state = states[id]
                val key = "notified:follow-up:$id"
                val prior =
                    app.toolDrafts.get(owner, key)?.let {
                        JsonParser.parseString(it).asJsonObject.get("dueAt").asString
                    }
                if (!FollowUpNotificationPolicy.shouldNotify(dueAt, state, prior)) continue
                if (app.ownerNow() != owner || !DebriefReminders.allowed(applicationContext))
                    return Result.success()
                if (FollowUpReminders.post(applicationContext, owner, id))
                    app.toolDrafts.save(
                        owner,
                        key,
                        JsonObject().apply { addProperty("dueAt", dueAt) }.toString(),
                    )
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
