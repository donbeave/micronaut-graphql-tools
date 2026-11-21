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

// The fixtures of the TCK scenario `sealed-types`: a union and an interface whose members are the permitted subclasses of
// a sealed type, and one member that the dictionary maps to a class of another name.

// tag::sealed-types[]
sealed interface SealedShape permits SealedCircle, SealedSquare, ThreeSidedShape {
}

@CompileStatic
@GraphQLType
final class SealedCircle implements SealedShape {
    final int radius = 3
}

@CompileStatic
@GraphQLType
final class SealedSquare implements SealedShape {
    final int side = 4
}

@CompileStatic
@GraphQLType
final class ThreeSidedShape implements SealedShape {
    final int base = 5
}

sealed interface SealedPet permits SealedDog, SealedCat {
    String getName()
}

@CompileStatic
@GraphQLType
final class SealedDog implements SealedPet {
    final String name = 'Rex'
    final boolean barks = true
}

@CompileStatic
@GraphQLType
final class SealedCat implements SealedPet {
    final String name = 'Tom'
    final int lives = 9
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'sealed-types')
class SealedTypesMappings implements TckTypeMappings {
    @Override
    void register(SchemaMappingDictionary dictionary) {
        dictionary.registerType('SealedTriangle', ThreeSidedShape)
    }
}
// end::sealed-types[]

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'sealed-types')
class SealedTypesQueries {

    List<SealedShape> sealedShapes() {
        return [new SealedCircle(), new SealedSquare(), new ThreeSidedShape()] as List<SealedShape>
    }

    List<SealedPet> sealedPets() {
        return [new SealedDog(), new SealedCat()] as List<SealedPet>
    }
}
