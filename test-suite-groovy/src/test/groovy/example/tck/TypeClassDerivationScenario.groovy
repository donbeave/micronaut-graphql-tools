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
package example.tck

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import jakarta.inject.Singleton

// The fixtures of the TCK scenario `type-class-derivation`: a union whose members need no dictionary entry, except the
// one that shows an entry winning over the naming convention.

interface DerivedResult {
}

// tag::type-classes[]
@CompileStatic
@GraphQLType
class DerivedBookGraph implements DerivedResult {
    final String title = 'by convention'
}

@CompileStatic
@GraphQLType(name = 'DerivedMagazine')
class MagazineModel implements DerivedResult {
    final int issue = 7
}

@CompileStatic
@GraphQLType
class DerivedPosterGraph implements DerivedResult {
    final String caption = 'by convention'
}

@CompileStatic
@GraphQLType
class PosterPrint implements DerivedResult {
    final String caption = 'by dictionary'
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'type-class-derivation')
class TypeClassDerivationMappings implements TckTypeMappings {
    @Override
    void register(SchemaMappingDictionary dictionary) {
        dictionary.registerType('DerivedPoster', PosterPrint)
    }
}
// end::type-classes[]

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'type-class-derivation')
class TypeClassDerivationQueries {

    List<DerivedResult> derivedResults() {
        return [new DerivedBookGraph(), new MagazineModel(), new PosterPrint()] as List<DerivedResult>
    }
}
