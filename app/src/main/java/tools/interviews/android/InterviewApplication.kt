package tools.interviews.android

import android.app.Application
import tools.interviews.android.data.InterviewDatabase
import tools.interviews.android.data.InterviewRepository

class InterviewApplication : Application() {
    val database by lazy { InterviewDatabase.getDatabase(this) }
    val repository by lazy { InterviewRepository(database.interviewDao()) }
}
