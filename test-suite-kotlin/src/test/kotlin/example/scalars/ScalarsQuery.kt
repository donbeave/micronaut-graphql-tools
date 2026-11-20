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

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// tag::resolver[]
@Requires(env = [SCALARS_DEFAULTS, SCALARS_RENAMED])
@GraphQLRootResolver
open class ScalarsQuery {

    open fun bookedOn(date: LocalDate): LocalDate = date.plusDays(1)

    open fun bookedAt(time: OffsetDateTime): OffsetDateTime = time.plusHours(1)

    open fun elapsed(spent: Duration): Duration = spent.plusMinutes(30)

    open fun payload(data: Any?): Any? = data

    open fun ticket(id: UUID): UUID = id

    open fun homepage(url: URL): URL = url
}
// end::resolver[]
