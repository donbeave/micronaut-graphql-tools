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
package example.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@MicronautTest
class HttpClientImplementationTest {

    @Inject
    lateinit var context: ApplicationContext

    @Test
    fun theTaskSelectsTheHttpClientImplementation() {
        val actual = context.getBean(HttpClient::class.java).javaClass.name
        if (System.getProperty("graphql.test.http-client") == "jdk") {
            assertThrows(ClassNotFoundException::class.java) { Class.forName("io.micronaut.http.client.netty.DefaultHttpClient") }
            assertEquals("io.micronaut.http.client.jdk.DefaultJdkHttpClient", actual)
        } else {
            assertEquals("io.micronaut.http.client.netty.DefaultHttpClient", actual)
        }
    }

}
