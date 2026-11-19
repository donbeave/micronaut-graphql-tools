/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.tck

import io.micronaut.graphql.tools.input.ArgumentValue
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
