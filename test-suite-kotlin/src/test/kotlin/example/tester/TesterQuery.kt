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
package example.tester

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLContextValue
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton

@GraphQLType
class TesterBook(val id: String, val title: String)

// tag::resolver[]
@Singleton
@GraphQLRootResolver
@Requires(env = [TESTER])
open class TesterQuery {

    open fun testerBook(id: String): TesterBook? = if (id == "none") null else TesterBook(id, "Book $id")

    open fun testerPages(count: Int): Int = count * 2

    open fun testerWhoAmI(@GraphQLContextValue("tester.user") user: String): String = user

    open fun testerFailing(): String? = throw IllegalStateException("boom")
}
// end::resolver[]
