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
package example.rename;

import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer;
import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

// The ways to bind a field to a member with another name (#107): @GraphQLField(name) on the members of the type, a
// coordinate on resolver methods, and the accessor prefixes.
@Factory
public class RenameFixtures {

    // @GraphQLField(name) on a method, a field and a getter, for fields named like Java keywords or Object methods
    public static final String MEMBERS = "rename-members";
    // a renamed member and a getter for one field
    public static final String CLASH = "rename-clash";
    // @GraphQLSchemaCoordinate on resolver methods with other names
    public static final String COORDINATES = "rename-coordinates";
    // getX and isX resolver methods (micronaut.graphql.tools.resolver-accessor-prefixes)
    public static final String PREFIXES = "rename-prefixes";

    @Bean
    @Singleton
    @Requires(env = MEMBERS)
    TypeDefinitionRegistryCustomizer membersSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Parcel { id: ID! class: String! hashCode: Int! headline: String! tagline: String! "
                        + "new(prefix: String!): String! }\n"
                        + "extend type Query { parcel: Parcel }\n"));
    }

    @Bean
    @Singleton
    @Requires(env = CLASH)
    TypeDefinitionRegistryCustomizer clashSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Slip { title: String! }\n"
                        + "extend type Query { slip: Slip }\n"));
    }

    @Bean
    @Singleton
    @Requires(env = COORDINATES)
    TypeDefinitionRegistryCustomizer coordinatesSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Parcel { id: ID! class: String! }\n"
                        + "extend type Query { parcel: Parcel default: String! }\n"));
    }

    @Bean
    @Singleton
    @Requires(env = PREFIXES)
    TypeDefinitionRegistryCustomizer prefixesSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Parcel { id: ID! author: String! active: Boolean! }\n"
                        + "extend type Query { parcel: Parcel stamp: String! }\n"));
    }

    @GraphQLType
    public static class Parcel {

        @GraphQLField("headline")
        private final String title = "Fragile";

        private final String subtitle = "Handle with care";

        public String getId() {
            return "1";
        }

        public String getTitle() {
            return title;
        }

        @GraphQLField("tagline")
        public String getSubtitle() {
            return subtitle;
        }

        @GraphQLField("class")
        public String kind() {
            return "parcel";
        }

        @GraphQLField("hashCode")
        public int digest() {
            return 42;
        }

        @GraphQLField("new")
        public String create(String prefix) {
            return prefix + " parcel";
        }

    }

    @Requires(env = MEMBERS)
    @Singleton
    @GraphQLRootResolver
    public static class ParcelQuery {

        public Parcel parcel() {
            return new Parcel();
        }

    }

    @GraphQLType
    public static class Slip {

        public String getTitle() {
            return "title";
        }

        @GraphQLField("title")
        public String heading() {
            return "heading";
        }

    }

    @Requires(env = CLASH)
    @Singleton
    @GraphQLRootResolver
    public static class SlipQuery {

        public Slip slip() {
            return new Slip();
        }

    }

    @GraphQLType
    public static class CoordinateParcel {

        public String getId() {
            return "1";
        }

    }

    @Requires(env = COORDINATES)
    @Singleton
    @GraphQLRootResolver
    public static class CoordinateQuery {

        @GraphQLSchemaCoordinate("Query.parcel")
        public CoordinateParcel fetchParcel() {
            return new CoordinateParcel();
        }

        @GraphQLSchemaCoordinate("Query.default")
        public String defaultValue() {
            return "plain";
        }

    }

    @Requires(env = COORDINATES)
    @Singleton
    @GraphQLTypeResolver(CoordinateParcel.class)
    public static class CoordinateParcelResolver {

        @GraphQLSchemaCoordinate("Parcel.class")
        public String kind(CoordinateParcel parcel) {
            return "parcel";
        }

    }

    @GraphQLType
    public static class PrefixParcel {

        public String getId() {
            return "1";
        }

    }

    @Requires(env = PREFIXES)
    @Singleton
    @GraphQLRootResolver
    public static class PrefixQuery {

        public PrefixParcel parcel() {
            return new PrefixParcel();
        }

        public String getStamp() {
            return "stamped";
        }

    }

    @Requires(env = PREFIXES)
    @Singleton
    @GraphQLTypeResolver(PrefixParcel.class)
    public static class PrefixParcelResolver {

        public String getAuthor(PrefixParcel parcel) {
            return "Jane";
        }

        public boolean isActive(PrefixParcel parcel) {
            return true;
        }

    }

}
