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
package example.scalars

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

// tag::resolver[]
@CompileStatic
@Requires(env = [ScalarsFixtures.DEFAULTS, ScalarsFixtures.RENAMED])
@GraphQLRootResolver
class ScalarsQuery {

    LocalDate bookedOn(LocalDate date) {
        return date.plusDays(1)
    }

    OffsetDateTime bookedAt(OffsetDateTime time) {
        return time.plusHours(1)
    }

    Duration elapsed(Duration spent) {
        return spent.plusMinutes(30)
    }

    Object payload(Object data) {
        return data
    }

    UUID ticket(UUID id) {
        return id
    }

    URL homepage(URL url) {
        return url
    }

}
// end::resolver[]
