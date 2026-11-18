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
import example.data.User
import example.data.UserRepository
import example.graphql.type.Profile
import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Nullable
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@CompileStatic
@GraphQLRootResolver
class SearchProfileQuery {

    private final UserRepository userRepository

    SearchProfileQuery(UserRepository userRepository) {
        this.userRepository = userRepository
    }

    @ElapsedTime
    List<Profile> searchProfile(String contains, @Nullable Integer limit) {
        List<User> results = userRepository.search(contains)
        if (limit != null) {
            results = results.subList(0, Integer.min(limit, results.size()))
        }
        return results.collect { new Profile(it) }
    }
}
