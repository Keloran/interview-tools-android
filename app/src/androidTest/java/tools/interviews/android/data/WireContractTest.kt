package tools.interviews.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import tools.interviews.android.data.api.ErrorResponse

@RunWith(AndroidJUnit4::class)
class WireContractTest {
    @Test fun minifiedReleaseRetainsServerErrorMessages() {
        val error = Gson().fromJson("{\"message\":\"Previous interview not found\"}", ErrorResponse::class.java)
        assertEquals("Previous interview not found", error.message)
    }
}
