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
package example.graphql;

import example.UserRepository;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class SearchProfileQuery {

    private final UserRepository userRepository;

    public SearchProfileQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<Profile> searchProfile(String contains, @Nullable Integer limit) {
        List<Profile> profiles = userRepository.search(contains).stream().map(Profile::new).toList();
        return limit == null ? profiles : profiles.subList(0, Math.min(limit, profiles.size()));
    }

}
