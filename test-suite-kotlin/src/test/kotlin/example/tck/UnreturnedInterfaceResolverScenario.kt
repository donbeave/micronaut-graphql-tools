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
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.mapping.SchemaMappingDictionary
import jakarta.inject.Singleton

// The fixtures of the TCK scenario `unreturned-interface-resolver`: a type resolver bound to an interface that no field
// returns, which the binding applies to every implementation.

interface Tagged {
    val id: String
}

@GraphQLType
class Badge : Tagged {
    override val id: String = "b1"
}

@GraphQLType
class Ribbon : Tagged {
    override val id: String = "r1"
}

@Singleton
@Requires(property = "tck.scenario", value = "unreturned-interface-resolver")
class UnreturnedInterfaceMappings : TckTypeMappings {
    override fun register(dictionary: SchemaMappingDictionary) {
        dictionary.registerType("Tagged", Tagged::class.java)
    }
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "unreturned-interface-resolver")
class UnreturnedInterfaceQueries {

    fun badge(): Badge = Badge()

    fun ribbon(): Ribbon = Ribbon()
}

@Singleton
@GraphQLTypeResolver(Tagged::class)
@Requires(property = "tck.scenario", value = "unreturned-interface-resolver")
class TaggedResolver {

    fun tag(tagged: Tagged): String = "tag of ${tagged.id}"
}
