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
package example.rename

import graphql.schema.idl.SchemaParser
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

// The ways to bind a field to a member with another name (#107): @GraphQLField(name) on the members of the type, a
// coordinate on resolver methods, and the accessor prefixes.
@CompileStatic
@Factory
class RenameFixtures {

    // @GraphQLField(name) on a method, a property and a getter, for fields named like keywords or Object methods
    static final String MEMBERS = 'rename-members'
    // a renamed member and a property for one field
    static final String CLASH = 'rename-clash'
    // @GraphQLSchemaCoordinate on resolver methods with other names
    static final String COORDINATES = 'rename-coordinates'
    // getX and isX resolver methods (micronaut.graphql.tools.resolver-accessor-prefixes)
    static final String PREFIXES = 'rename-prefixes'

    @Bean
    @Singleton
    @Requires(env = RenameFixtures.MEMBERS)
    TypeDefinitionRegistryCustomizer membersSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Parcel { id: ID! class: String! hashCode: Int! headline: String! tagline: String! ' +
                            'new(prefix: String!): String! }\n' +
                            'extend type Query { parcel: Parcel }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = RenameFixtures.CLASH)
    TypeDefinitionRegistryCustomizer clashSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Slip { title: String! }\n' +
                            'extend type Query { slip: Slip }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = RenameFixtures.COORDINATES)
    TypeDefinitionRegistryCustomizer coordinatesSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Parcel { id: ID! class: String! }\n' +
                            'extend type Query { parcel: Parcel default: String! }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = RenameFixtures.PREFIXES)
    TypeDefinitionRegistryCustomizer prefixesSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'type Parcel { id: ID! author: String! active: Boolean! }\n' +
                            'extend type Query { parcel: Parcel stamp: String! }\n'))
        } as TypeDefinitionRegistryCustomizer
    }

}

@CompileStatic
@GraphQLType
class Parcel {

    String id = '1'

    @GraphQLField('headline')
    String title = 'Fragile'

    private String subtitle = 'Handle with care'

    @GraphQLField('tagline')
    String getSubtitle() {
        return subtitle
    }

    @GraphQLField('class')
    String kind() {
        return 'parcel'
    }

    @GraphQLField('hashCode')
    int digest() {
        return 42
    }

    @GraphQLField('new')
    String create(String prefix) {
        return prefix + ' parcel'
    }

}

@CompileStatic
@Requires(env = RenameFixtures.MEMBERS)
@GraphQLRootResolver
class ParcelQuery {

    Parcel parcel() {
        return new Parcel()
    }

}

@CompileStatic
@GraphQLType
class Slip {

    String title = 'title'

    @GraphQLField('title')
    String heading() {
        return 'heading'
    }

}

@CompileStatic
@Requires(env = RenameFixtures.CLASH)
@GraphQLRootResolver
class SlipQuery {

    Slip slip() {
        return new Slip()
    }

}

@CompileStatic
@GraphQLType
class CoordinateParcel {
    String id = '1'
}

@CompileStatic
@Requires(env = RenameFixtures.COORDINATES)
@GraphQLRootResolver
class CoordinateQuery {

    @GraphQLSchemaCoordinate('Query.parcel')
    CoordinateParcel fetchParcel() {
        return new CoordinateParcel()
    }

    @GraphQLSchemaCoordinate('Query.default')
    String defaultValue() {
        return 'plain'
    }

}

@CompileStatic
@Requires(env = RenameFixtures.COORDINATES)
@GraphQLTypeResolver(CoordinateParcel)
class CoordinateParcelResolver {

    @GraphQLSchemaCoordinate('Parcel.class')
    String kind(CoordinateParcel parcel) {
        return 'parcel'
    }

}

@CompileStatic
@GraphQLType
class PrefixParcel {
    String id = '1'
}

@CompileStatic
@Requires(env = RenameFixtures.PREFIXES)
@GraphQLRootResolver
class PrefixQuery {

    PrefixParcel parcel() {
        return new PrefixParcel()
    }

    String getStamp() {
        return 'stamped'
    }

}

@CompileStatic
@Requires(env = RenameFixtures.PREFIXES)
@GraphQLTypeResolver(PrefixParcel)
class PrefixParcelResolver {

    String getAuthor(PrefixParcel parcel) {
        return 'Jane'
    }

    boolean isActive(PrefixParcel parcel) {
        return true
    }

}
