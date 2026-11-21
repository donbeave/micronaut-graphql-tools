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
package example.manifest

import io.micronaut.graphql.tools.manifest.BindingManifest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest(startApplication = false)
class BindingManifestTest {

    @Inject
    lateinit var bindingManifest: BindingManifest

    @Test
    fun `the manifest of the sample equals the golden file`() {
        val golden = javaClass.getResourceAsStream("/binding-manifest.txt")!!.use { String(it.readAllBytes(), Charsets.UTF_8) }
        assertEquals(golden, bindingManifest.lines.joinToString("\n") + "\n")
    }

    @Test
    fun `a resolver overriding Object methods has no unbound finding`() {
        assertTrue(
            bindingManifest.lines.none {
                it.startsWith("!unbound-resolver-method ") && (it.contains("toString") || it.contains("equals") || it.contains("hashCode"))
            },
            bindingManifest.lines.toString(),
        )
    }
}
