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
package example.drift

import example.graphql.type.Profile
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

/**
 * A resolver that drifts from the schema on purpose: the argument of `Query.searchProfile` is named `limit`, and the parameter is
 * named `count`. The compilation of this source set must fail with `[argument-mismatch] Query.searchProfile` (task
 * `driftFixtureTest`). Nothing compiles it into the application.
 */
@GraphQLRootResolver
open class DriftedSearchProfileQuery {

    open fun searchProfile(contains: String, count: Int?): List<Profile> = emptyList()
}
