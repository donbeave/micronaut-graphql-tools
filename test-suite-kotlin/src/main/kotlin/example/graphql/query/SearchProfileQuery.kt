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
package example.graphql.query

import example.ElapsedTime
import example.data.UserRepository
import example.graphql.type.Profile
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
open class SearchProfileQuery(private val userRepository: UserRepository) {

    @ElapsedTime
    open fun searchProfile(contains: String, limit: Int?): List<Profile> {
        var results = userRepository.search(contains)
        if (limit != null) {
            results = results.subList(0, minOf(limit, results.size))
        }
        return results.map { Profile(it) }
    }
}
