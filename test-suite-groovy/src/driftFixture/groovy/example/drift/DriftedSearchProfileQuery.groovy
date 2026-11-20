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

import jakarta.inject.Singleton
import example.graphql.type.Profile
import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

// tag::drifted[]
/**
 * A resolver that drifts from the schema on purpose: the argument of {@code Query.searchProfile} is named {@code limit}, and the
 * parameter is named {@code count}. The compilation of this source set must fail with
 * {@code [argument-mismatch] Query.searchProfile} (task {@code driftFixtureTest}). Nothing compiles it into the application.
 */
@CompileStatic
@Singleton
@GraphQLRootResolver
class DriftedSearchProfileQuery {

    List<Profile> searchProfile(String contains, @Nullable Integer count) {
        return []
    }
}
// end::drifted[]
