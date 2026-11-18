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
package example.deepchain

import graphql.GraphQL
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Runs on the JVM and as a native binary (nativeTest): a chain of 200 object types and 200 input types is bound and
// queried on the default thread stack.
@MicronautTest(environments = [DEEP_TYPE_CHAIN_ENVIRONMENT])
class DeepTypeChainTest {

    @Inject
    lateinit var graphQL: GraphQL

    @Test
    fun aChainOfTypesIsBound() {
        val obj = graphQL.execute("{ chainRoot { name next { name } } }")
        assertTrue(obj.errors.isEmpty(), obj.errors.toString())
        assertEquals(mapOf("chainRoot" to mapOf("name" to "n", "next" to mapOf("name" to "n"))), obj.getData<Any>())

        val input = graphQL.execute("{ chainEcho(input: {name: \"a\", next: {name: \"b\"}}) }")
        assertTrue(input.errors.isEmpty(), input.errors.toString())
        assertEquals(mapOf("chainEcho" to "a>b"), input.getData<Any>())
    }
}
