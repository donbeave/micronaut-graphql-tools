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

// The fixtures of the TCK scenario `sealed-types`: a union and an interface whose members are the permitted subclasses of
// a sealed type, and one member that the dictionary maps to a class of another name.

// tag::sealed-types[]
sealed interface SealedShape

@GraphQLType
class SealedCircle : SealedShape {
    val radius: Int = 3
}

@GraphQLType
class SealedSquare : SealedShape {
    val side: Int = 4
}

@GraphQLType
class ThreeSidedShape : SealedShape {
    val base: Int = 5
}

sealed interface SealedPet {
    val name: String
}

@GraphQLType
class SealedDog : SealedPet {
    override val name: String = "Rex"
    val barks: Boolean = true
}

@GraphQLType
class SealedCat : SealedPet {
    override val name: String = "Tom"
    val lives: Int = 9
}

@Singleton
@Requires(property = "tck.scenario", value = "sealed-types")
class SealedTypesMappings : TckTypeMappings {
    override fun register(dictionary: SchemaMappingDictionary) {
        dictionary.registerType("SealedTriangle", ThreeSidedShape::class.java)
    }
}
// end::sealed-types[]

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "sealed-types")
class SealedTypesQueries {

    fun sealedShapes(): List<SealedShape> = listOf(SealedCircle(), SealedSquare(), ThreeSidedShape())

    fun sealedPets(): List<SealedPet> = listOf(SealedDog(), SealedCat())
}
