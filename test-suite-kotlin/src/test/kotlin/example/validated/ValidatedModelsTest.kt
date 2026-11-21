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
package example.validated

import example.validated.generated.Generated
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The classes that @GraphQLModels lists keep their introspections when Micronaut Validation's processor runs in the same
// compilation (#528). Runs on the JVM and as a native binary (nativeTest).
class ValidatedModelsTest {

    private fun introspections(type: Class<*>) =
        BeanIntrospector.SHARED.findIntrospections { it.beanType == type }.size

    @Test
    fun `a listed class is introspected`() {
        assertTrue(BeanIntrospection.getIntrospection(Plain::class.java).getProperty("name").isPresent)
        assertTrue(BeanIntrospection.getIntrospection(Listed::class.java).getProperty("label").isPresent)
    }

    @Test
    fun `the classes of a listed package are introspected`() {
        assertTrue(BeanIntrospection.getIntrospection(Generated::class.java).getProperty("id").isPresent)
    }

    @Test
    fun `a direct Introspected keeps working`() {
        assertEquals(1, introspections(Direct::class.java))
    }

    @Test
    fun `a class listed twice is introspected once`() {
        assertEquals(1, introspections(Twice::class.java))
    }
}
