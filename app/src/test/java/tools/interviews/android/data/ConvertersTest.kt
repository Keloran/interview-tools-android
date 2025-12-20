package tools.interviews.android.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setup() {
        converters = Converters()
    }

    // ==================== LocalDate Tests ====================

    @Test
    fun `fromLocalDate converts date to ISO string`() {
        val date = LocalDate.of(2024, 6, 15)
        val result = converters.fromLocalDate(date)
        assertEquals("2024-06-15", result)
    }

    @Test
    fun `fromLocalDate handles null`() {
        val result = converters.fromLocalDate(null)
        assertNull(result)
    }

    @Test
    fun `fromLocalDate handles edge date - year boundary`() {
        val date = LocalDate.of(2024, 12, 31)
        val result = converters.fromLocalDate(date)
        assertEquals("2024-12-31", result)
    }

    @Test
    fun `fromLocalDate handles leap year date`() {
        val date = LocalDate.of(2024, 2, 29)
        val result = converters.fromLocalDate(date)
        assertEquals("2024-02-29", result)
    }

    @Test
    fun `toLocalDate parses valid ISO string`() {
        val result = converters.toLocalDate("2024-06-15")
        assertEquals(LocalDate.of(2024, 6, 15), result)
    }

    @Test
    fun `toLocalDate handles null`() {
        val result = converters.toLocalDate(null)
        assertNull(result)
    }

    @Test
    fun `toLocalDate throws on invalid format`() {
        assertThrows(DateTimeParseException::class.java) {
            converters.toLocalDate("15/06/2024")
        }
    }

    @Test
    fun `toLocalDate throws on invalid date`() {
        assertThrows(DateTimeParseException::class.java) {
            converters.toLocalDate("2024-02-30") // Feb 30 doesn't exist
        }
    }

    @Test
    fun `toLocalDate throws on empty string`() {
        assertThrows(DateTimeParseException::class.java) {
            converters.toLocalDate("")
        }
    }

    @Test
    fun `toLocalDate throws on garbage input`() {
        assertThrows(DateTimeParseException::class.java) {
            converters.toLocalDate("not-a-date")
        }
    }

    @Test
    fun `LocalDate roundtrip preserves value`() {
        val original = LocalDate.of(2024, 7, 4)
        val serialized = converters.fromLocalDate(original)
        val deserialized = converters.toLocalDate(serialized)
        assertEquals(original, deserialized)
    }

    // ==================== LocalDateTime Tests ====================

    @Test
    fun `fromLocalDateTime converts datetime to ISO string`() {
        val dateTime = LocalDateTime.of(2024, 6, 15, 14, 30, 0)
        val result = converters.fromLocalDateTime(dateTime)
        assertEquals("2024-06-15T14:30", result)
    }

    @Test
    fun `fromLocalDateTime includes seconds when present`() {
        val dateTime = LocalDateTime.of(2024, 6, 15, 14, 30, 45)
        val result = converters.fromLocalDateTime(dateTime)
        assertEquals("2024-06-15T14:30:45", result)
    }

    @Test
    fun `fromLocalDateTime handles null`() {
        val result = converters.fromLocalDateTime(null)
        assertNull(result)
    }

    @Test
    fun `fromLocalDateTime handles midnight`() {
        val dateTime = LocalDateTime.of(2024, 6, 15, 0, 0, 0)
        val result = converters.fromLocalDateTime(dateTime)
        assertEquals("2024-06-15T00:00", result)
    }

    @Test
    fun `fromLocalDateTime handles end of day`() {
        val dateTime = LocalDateTime.of(2024, 6, 15, 23, 59, 59)
        val result = converters.fromLocalDateTime(dateTime)
        assertEquals("2024-06-15T23:59:59", result)
    }

    @Test
    fun `toLocalDateTime parses valid ISO string`() {
        val result = converters.toLocalDateTime("2024-06-15T14:30:00")
        assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30, 0), result)
    }

    @Test
    fun `toLocalDateTime parses without seconds`() {
        val result = converters.toLocalDateTime("2024-06-15T14:30")
        assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30, 0), result)
    }

    @Test
    fun `toLocalDateTime handles null`() {
        val result = converters.toLocalDateTime(null)
        assertNull(result)
    }

    @Test
    fun `toLocalDateTime throws on date-only string`() {
        assertThrows(DateTimeParseException::class.java) {
            converters.toLocalDateTime("2024-06-15")
        }
    }

    @Test
    fun `toLocalDateTime throws on invalid format`() {
        assertThrows(DateTimeParseException::class.java) {
            converters.toLocalDateTime("15/06/2024 14:30")
        }
    }

    @Test
    fun `toLocalDateTime throws on invalid time`() {
        assertThrows(DateTimeParseException::class.java) {
            converters.toLocalDateTime("2024-06-15T25:00:00") // Hour 25 doesn't exist
        }
    }

    @Test
    fun `LocalDateTime roundtrip preserves value`() {
        val original = LocalDateTime.of(2024, 7, 4, 16, 45, 30)
        val serialized = converters.fromLocalDateTime(original)
        val deserialized = converters.toLocalDateTime(serialized)
        assertEquals(original, deserialized)
    }

    // ==================== InterviewStage Tests ====================

    @Test
    fun `fromInterviewStage converts all stages correctly`() {
        InterviewStage.entries.forEach { stage ->
            val result = converters.fromInterviewStage(stage)
            assertEquals(stage.name, result)
        }
    }

    @Test
    fun `toInterviewStage parses all valid stage names`() {
        InterviewStage.entries.forEach { stage ->
            val result = converters.toInterviewStage(stage.name)
            assertEquals(stage, result)
        }
    }

    @Test
    fun `toInterviewStage throws on invalid stage name`() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toInterviewStage("INVALID_STAGE")
        }
    }

    @Test
    fun `toInterviewStage throws on lowercase stage name`() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toInterviewStage("applied") // Should be APPLIED
        }
    }

    @Test
    fun `toInterviewStage throws on empty string`() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toInterviewStage("")
        }
    }

    @Test
    fun `InterviewStage roundtrip preserves value`() {
        InterviewStage.entries.forEach { original ->
            val serialized = converters.fromInterviewStage(original)
            val deserialized = converters.toInterviewStage(serialized)
            assertEquals(original, deserialized)
        }
    }

    // ==================== InterviewMethod Tests ====================

    @Test
    fun `fromInterviewMethod converts all methods correctly`() {
        InterviewMethod.entries.forEach { method ->
            val result = converters.fromInterviewMethod(method)
            assertEquals(method.name, result)
        }
    }

    @Test
    fun `fromInterviewMethod handles null`() {
        val result = converters.fromInterviewMethod(null)
        assertNull(result)
    }

    @Test
    fun `toInterviewMethod parses all valid method names`() {
        InterviewMethod.entries.forEach { method ->
            val result = converters.toInterviewMethod(method.name)
            assertEquals(method, result)
        }
    }

    @Test
    fun `toInterviewMethod handles null`() {
        val result = converters.toInterviewMethod(null)
        assertNull(result)
    }

    @Test
    fun `toInterviewMethod throws on invalid method name`() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toInterviewMethod("INVALID_METHOD")
        }
    }

    @Test
    fun `toInterviewMethod throws on display name instead of enum name`() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toInterviewMethod("Video Call") // Should be VIDEO_CALL
        }
    }

    @Test
    fun `InterviewMethod roundtrip preserves value`() {
        InterviewMethod.entries.forEach { original ->
            val serialized = converters.fromInterviewMethod(original)
            val deserialized = converters.toInterviewMethod(serialized)
            assertEquals(original, deserialized)
        }
    }

    @Test
    fun `InterviewMethod roundtrip handles null`() {
        val serialized = converters.fromInterviewMethod(null)
        val deserialized = converters.toInterviewMethod(serialized)
        assertNull(deserialized)
    }

    // ==================== InterviewOutcome Tests ====================

    @Test
    fun `fromInterviewOutcome converts all outcomes correctly`() {
        InterviewOutcome.entries.forEach { outcome ->
            val result = converters.fromInterviewOutcome(outcome)
            assertEquals(outcome.name, result)
        }
    }

    @Test
    fun `toInterviewOutcome parses all valid outcome names`() {
        InterviewOutcome.entries.forEach { outcome ->
            val result = converters.toInterviewOutcome(outcome.name)
            assertEquals(outcome, result)
        }
    }

    @Test
    fun `toInterviewOutcome throws on invalid outcome name`() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toInterviewOutcome("INVALID_OUTCOME")
        }
    }

    @Test
    fun `toInterviewOutcome throws on display name instead of enum name`() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toInterviewOutcome("Awaiting Response") // Should be AWAITING_RESPONSE
        }
    }

    @Test
    fun `toInterviewOutcome throws on partial match`() {
        assertThrows(IllegalArgumentException::class.java) {
            converters.toInterviewOutcome("PASSED_WITH_FLYING_COLORS")
        }
    }

    @Test
    fun `InterviewOutcome roundtrip preserves value`() {
        InterviewOutcome.entries.forEach { original ->
            val serialized = converters.fromInterviewOutcome(original)
            val deserialized = converters.toInterviewOutcome(serialized)
            assertEquals(original, deserialized)
        }
    }
}