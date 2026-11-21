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
 * The fixtures of the TCK scenario {@code sealed-types}: a union and an interface whose members are the permitted subclasses of
 * a sealed type, and one member that the dictionary maps to a class of another name.
 */
public final class SealedTypesScenario {

    private SealedTypesScenario() {
    }

    // tag::sealed-types[]
    public sealed interface SealedShape permits SealedCircle, SealedSquare, ThreeSidedShape {
    }

    @GraphQLType
    public static final class SealedCircle implements SealedShape {
        public int getRadius() {
            return 3;
        }
    }

    @GraphQLType
    public static final class SealedSquare implements SealedShape {
        public int getSide() {
            return 4;
        }
    }

    @GraphQLType
    public static final class ThreeSidedShape implements SealedShape {
        public int getBase() {
            return 5;
        }
    }

    public sealed interface SealedPet permits SealedDog, SealedCat {
        String getName();
    }

    @GraphQLType
    public static final class SealedDog implements SealedPet {
        @Override
        public String getName() {
            return "Rex";
        }

        public boolean isBarks() {
            return true;
        }
    }

    @GraphQLType
    public static final class SealedCat implements SealedPet {
        @Override
        public String getName() {
            return "Tom";
        }

        public int getLives() {
            return 9;
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "sealed-types")
    public static class Mappings implements TckTypeMappings {
        @Override
        public void register(SchemaMappingDictionary dictionary) {
            dictionary.registerType("SealedTriangle", ThreeSidedShape.class);
        }
    }
    // end::sealed-types[]

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "sealed-types")
    public static class Queries {

        public List<SealedShape> sealedShapes() {
            return List.of(new SealedCircle(), new SealedSquare(), new ThreeSidedShape());
        }

        public List<SealedPet> sealedPets() {
            return List.of(new SealedDog(), new SealedCat());
        }
    }

}
