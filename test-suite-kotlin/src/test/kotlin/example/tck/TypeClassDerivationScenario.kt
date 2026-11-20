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

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import jakarta.inject.Singleton

// The fixtures of the TCK scenario `type-class-derivation`: a union whose members need no dictionary entry, except the
// one that shows an entry winning over the naming convention.

interface DerivedResult

// tag::type-classes[]
@GraphQLType
class DerivedBookGraph : DerivedResult {
    val title: String = "by convention"
}

@GraphQLType(name = "DerivedMagazine")
class MagazineModel : DerivedResult {
    val issue: Int = 7
}

@GraphQLType
class DerivedPosterGraph : DerivedResult {
    val caption: String = "by convention"
}

@GraphQLType
class PosterPrint : DerivedResult {
    val caption: String = "by dictionary"
}

@Singleton
@Requires(property = "tck.scenario", value = "type-class-derivation")
class TypeClassDerivationMappings : TckTypeMappings {
    override fun register(dictionary: SchemaMappingDictionary) {
        dictionary.registerType("DerivedPoster", PosterPrint::class.java)
    }
}
// end::type-classes[]

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "type-class-derivation")
class TypeClassDerivationQueries {

    fun derivedResults(): List<DerivedResult> = listOf(DerivedBookGraph(), MagazineModel(), PosterPrint())
}
