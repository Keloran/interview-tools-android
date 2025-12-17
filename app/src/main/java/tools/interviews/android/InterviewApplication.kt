package tools.interviews.android

import android.app.Application
import com.clerk.api.Clerk
import tools.interviews.android.data.InterviewDatabase
import tools.interviews.android.data.InterviewRepository
import tools.interviews.android.data.api.SyncService

class InterviewApplication : Application() {
    val database by lazy { InterviewDatabase.getDatabase(this) }
    val repository by lazy { InterviewRepository(database.interviewDao()) }
    val syncService by lazy {
        SyncService(
            interviewDao = database.interviewDao(),
            companyDao = database.companyDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(
            context = this,
            publishableKey = "pk_live_Y2xlcmsuaW50ZXJ2aWV3cy50b29scyQ"
        )
    }
}
