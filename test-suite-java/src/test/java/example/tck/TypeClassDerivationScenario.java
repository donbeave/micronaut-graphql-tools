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
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * The fixtures of the TCK scenario {@code type-class-derivation}: a union whose members need no dictionary entry, except the
 * one that shows an entry winning over the naming convention.
 */
public final class TypeClassDerivationScenario {

    private TypeClassDerivationScenario() {
    }

    public interface DerivedResult {
    }

    // tag::type-classes[]
    @GraphQLType
    public static class DerivedBookGraph implements DerivedResult {
        public String getTitle() {
            return "by convention";
        }
    }

    @GraphQLType(name = "DerivedMagazine")
    public static class MagazineModel implements DerivedResult {
        public int getIssue() {
            return 7;
        }
    }

    @GraphQLType
    public static class DerivedPosterGraph implements DerivedResult {
        public String getCaption() {
            return "by convention";
        }
    }

    @GraphQLType
    public static class PosterPrint implements DerivedResult {
        public String getCaption() {
            return "by dictionary";
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "type-class-derivation")
    public static class Mappings implements TckTypeMappings {
        @Override
        public void register(SchemaMappingDictionary dictionary) {
            dictionary.registerType("DerivedPoster", PosterPrint.class);
        }
    }
    // end::type-classes[]

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "type-class-derivation")
    public static class Queries {

        public List<DerivedResult> derivedResults() {
            return List.of(new DerivedBookGraph(), new MagazineModel(), new PosterPrint());
        }
    }

}
