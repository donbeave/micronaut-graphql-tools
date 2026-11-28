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

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import java.util.concurrent.atomic.AtomicInteger

/**
 * A server filter in front of the GraphQL endpoint of the application: it records what the clients of the tests send, and it
 * answers 503 to the next requests when a test asks for failures.
 */
@ServerFilter("/graphql")
class RequestRecorder {

    private val requests = AtomicInteger()
    private val failures = AtomicInteger()

    @Volatile
    private var last: HttpRequest<*>? = null

    @RequestFilter
    fun record(request: HttpRequest<*>): HttpResponse<*>? {
        requests.incrementAndGet()
        last = request
        return if (failures.getAndUpdate { left -> maxOf(0, left - 1) } > 0) HttpResponse.status<Any>(HttpStatus.SERVICE_UNAVAILABLE) else null
    }

    fun failNext(count: Int) {
        failures.set(count)
    }

    fun requests(): Int = requests.get()

    fun header(name: String): String? = last?.headers?.get(name)
}
