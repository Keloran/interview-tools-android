package tools.interviews.android.util

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
import kotlinx.coroutines.launch

fun AppCompatActivity.finishOnAccountChange() {
    val original = Clerk.user?.id
    lifecycleScope.launch {
        Clerk.userFlow.collect { if (it?.id != original) finish() }
    }
}
