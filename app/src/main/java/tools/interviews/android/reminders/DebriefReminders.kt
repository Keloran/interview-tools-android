package tools.interviews.android.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.clerk.api.Clerk
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tools.interviews.android.InterviewApplication
import tools.interviews.android.R
import tools.interviews.android.StageReflectionActivity
import tools.interviews.android.data.InterviewDao
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.model.*

object DebriefPolicy {
    fun end(row: Interview): Instant? =
        if (row.hasMeeting) row.interviewInstant?.plusSeconds(row.meetingDurationMinutes * 60L)
        else null

    fun scheduled(row: Interview): Boolean =
        row.outcome == InterviewOutcome.SCHEDULED &&
            row.debriefedAt == null &&
            end(row) != null &&
            row.debriefPromptedFor != end(row).toString()

    fun recent(row: Interview, now: Instant): Boolean =
        end(row)?.isBefore(now.minus(Duration.ofDays(2))) == false

    fun due(row: Interview, now: Instant): Boolean =
        scheduled(row) && recent(row, now) && !requireNotNull(end(row)).isAfter(now)
}

class DebriefReminderRunner(
    private val dao: InterviewDao,
    private val gate: AccountOperationGate,
    private val ownerNow: () -> String,
    private val allowed: () -> Boolean,
    private val post: (Interview) -> Boolean,
) {
    suspend fun run(owner: String, id: Long, now: Instant): Boolean = gate.run {
        if (ownerNow() != owner || !allowed()) return@run false
        val row = dao.getInterviewById(id, owner) ?: return@run false
        if (ownerNow() != owner || !allowed() || !DebriefPolicy.due(row, now)) return@run false
        if (!post(row)) return@run false
        if (ownerNow() == owner)
            dao.update(row.copy(debriefPromptedFor = DebriefPolicy.end(row).toString()))
        true
    }
}

object DebriefReminders {
    const val TAG = "account-debrief-reminders"
    private const val CHANNEL = "interview-debrief"

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun schedule(context: Context, rows: List<Interview>) {
        if (!allowed(context)) return
        val manager = WorkManager.getInstance(context)
        for (row in rows) {
            val name = "debrief:${row.ownerId}:${row.id}"
            if (!DebriefPolicy.scheduled(row) || !DebriefPolicy.recent(row, Instant.now())) {
                manager.cancelUniqueWork(name)
                continue
            }
            val delay =
                Duration.between(Instant.now(), DebriefPolicy.end(row)).toMillis().coerceAtLeast(0)
            val work =
                OneTimeWorkRequestBuilder<DebriefReminderWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("owner" to row.ownerId, "interviewId" to row.id))
                    .addTag(TAG)
                    .build()
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, work)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun post(context: Context, row: Interview): Boolean {
        if (!allowed(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Interview debriefs",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val intent =
            Intent(context, StageReflectionActivity::class.java)
                .putExtra(StageReflectionActivity.EXTRA_INTERVIEW_ID, row.id)
                .putExtra("expected_owner", row.ownerId)
        val code = "${row.ownerId}:${row.id}".hashCode()
        val pending =
            PendingIntent.getActivity(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_calendar_empty)
                .setContentTitle("How did it go?")
                .setContentText("Record a quick debrief of your interview.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        // Permission is checked immediately above, and may be revoked while the worker runs.
        return try {
            manager.notify(TAG, code, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

class DebriefReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        val id = inputData.getLong("interviewId", -1)
        if (id < 0) return Result.failure()
        if (withTimeoutOrNull(15000) { Clerk.isInitialized.first { it } } != true)
            return Result.retry()
        val app = applicationContext as InterviewApplication
        return try {
            DebriefReminderRunner(
                    app.database.interviewDao(),
                    app.accountGate,
                    app::ownerNow,
                    { DebriefReminders.allowed(applicationContext) },
                    { DebriefReminders.post(applicationContext, it) },
                )
                .run(owner, id, Instant.now())
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
