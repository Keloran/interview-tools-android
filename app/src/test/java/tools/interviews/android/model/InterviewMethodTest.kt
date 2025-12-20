package tools.interviews.android.model

import org.junit.Assert.*
import org.junit.Test

class InterviewMethodTest {

    @Test
    fun `InterviewMethod has correct number of values`() {
        assertEquals(3, InterviewMethod.entries.size)
    }

    @Test
    fun `InterviewMethod VIDEO_CALL has correct displayName`() {
        assertEquals("Video Call", InterviewMethod.VIDEO_CALL.displayName)
    }

    @Test
    fun `InterviewMethod PHONE_CALL has correct displayName`() {
        assertEquals("Phone Call", InterviewMethod.PHONE_CALL.displayName)
    }

    @Test
    fun `InterviewMethod IN_PERSON has correct displayName`() {
        assertEquals("In Person", InterviewMethod.IN_PERSON.displayName)
    }

    @Test
    fun `all methods have non-empty displayNames`() {
        InterviewMethod.entries.forEach { method ->
            assertTrue(
                "Method ${method.name} has empty displayName",
                method.displayName.isNotEmpty()
            )
        }
    }

    @Test
    fun `all methods have unique displayNames`() {
        val displayNames = InterviewMethod.entries.map { it.displayName }
        assertEquals(
            "Duplicate displayNames found",
            displayNames.size,
            displayNames.toSet().size
        )
    }

    @Test
    fun `valueOf returns correct method for valid names`() {
        InterviewMethod.entries.forEach { method ->
            assertEquals(method, InterviewMethod.valueOf(method.name))
        }
    }

    @Test
    fun `valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewMethod.valueOf("INVALID_METHOD")
        }
    }

    @Test
    fun `valueOf throws for lowercase name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewMethod.valueOf("video_call")
        }
    }

    @Test
    fun `valueOf throws for display name`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewMethod.valueOf("Video Call")
        }
    }

    @Test
    fun `valueOf throws for empty string`() {
        assertThrows(IllegalArgumentException::class.java) {
            InterviewMethod.valueOf("")
        }
    }

    @Test
    fun `methods are in expected order`() {
        val expectedOrder = listOf(
            InterviewMethod.VIDEO_CALL,
            InterviewMethod.PHONE_CALL,
            InterviewMethod.IN_PERSON
        )
        assertEquals(expectedOrder, InterviewMethod.entries.toList())
    }

    @Test
    fun `ordinal values are sequential starting from 0`() {
        InterviewMethod.entries.forEachIndexed { index, method ->
            assertEquals("Ordinal mismatch for ${method.name}", index, method.ordinal)
        }
    }

    @Test
    fun `displayNames are human readable with spaces`() {
        InterviewMethod.entries.forEach { method ->
            // Each displayName should contain a space (two words)
            assertTrue(
                "DisplayName '${method.displayName}' should contain a space",
                method.displayName.contains(" ")
            )
        }
    }

    @Test
    fun `displayNames are title case`() {
        InterviewMethod.entries.forEach { method ->
            val words = method.displayName.split(" ")
            words.forEach { word ->
                assertTrue(
                    "Word '$word' in ${method.displayName} should start with uppercase",
                    word[0].isUpperCase()
                )
            }
        }
    }
}