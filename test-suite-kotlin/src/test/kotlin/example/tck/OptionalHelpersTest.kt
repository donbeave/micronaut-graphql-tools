package example.tck

import io.micronaut.graphql.tools.ArgumentValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OptionalHelpersTest {

    @Test
    fun onlyAProvidedNonNullValueIsPresent() {
        assertEquals("absent", describePresence(ArgumentValue.omitted()))
        assertEquals("absent", describePresence(ArgumentValue.ofNullable(null)))
        assertEquals("present:a", describePresence(ArgumentValue.ofNullable("a")))

        val seen = mutableListOf<String>()
        rememberIfPresent(ArgumentValue.omitted(), seen)
        rememberIfPresent(ArgumentValue.ofNullable(null), seen)
        rememberIfPresent(ArgumentValue.ofNullable("a"), seen)
        assertEquals(listOf("a"), seen)
    }

}
