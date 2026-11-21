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

import io.micronaut.graphql.tools.tck.HttpTck
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

/**
 * The HTTP transport checks in the Kotlin suite: a real HTTP client against the running application.
 * The same checks run in the Java and Groovy suites.
 */
class HttpTest {

    @TestFactory
    fun checks(): Stream<DynamicTest> =
        HttpTck.checks().stream().map { check -> DynamicTest.dynamicTest(check) { HttpTck.run(check) } }
}
