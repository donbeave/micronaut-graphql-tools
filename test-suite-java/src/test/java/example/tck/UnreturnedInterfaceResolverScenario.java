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
package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary;
import jakarta.inject.Singleton;

/**
 * The fixtures of the TCK scenario {@code unreturned-interface-resolver}: a type resolver bound to an interface that no field
 * returns, which the binding applies to every implementation.
 */
public final class UnreturnedInterfaceResolverScenario {

    private UnreturnedInterfaceResolverScenario() {
    }

    public interface Tagged {
        String getId();
    }

    @GraphQLType
    public static class Badge implements Tagged {
        @Override
        public String getId() {
            return "b1";
        }
    }

    @GraphQLType
    public static class Ribbon implements Tagged {
        @Override
        public String getId() {
            return "r1";
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "unreturned-interface-resolver")
    public static class Mappings implements TckTypeMappings {
        @Override
        public void register(SchemaMappingDictionary dictionary) {
            dictionary.registerType("Tagged", Tagged.class);
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "unreturned-interface-resolver")
    public static class Queries {

        public Badge badge() {
            return new Badge();
        }

        public Ribbon ribbon() {
            return new Ribbon();
        }
    }

    @Singleton
    @GraphQLTypeResolver(Tagged.class)
    @Requires(property = "tck.scenario", value = "unreturned-interface-resolver")
    public static class TaggedResolver {

        public String tag(Tagged tagged) {
            return "tag of " + tagged.getId();
        }
    }
}
