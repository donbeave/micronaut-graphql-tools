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
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton

// The ways to bind a field to a member with another name (#107): @GraphQLField(name) on the members of the type, a
// coordinate on resolver methods, and the accessor prefixes.

// @GraphQLField(name) on a function, a property and a getter, for fields named like keywords or Object methods
const val RENAME_MEMBERS = "rename-members"

// a renamed member and a property for one field
const val RENAME_CLASH = "rename-clash"

// @GraphQLSchemaCoordinate on resolver methods with other names
const val RENAME_COORDINATES = "rename-coordinates"

// getX and isX resolver methods (micronaut.graphql.tools.resolver-accessor-prefixes)
const val RENAME_PREFIXES = "rename-prefixes"

@Factory
class RenameFixtures {

    @Bean
    @Singleton
    @Requires(env = [RENAME_MEMBERS])
    fun membersSchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Parcel { id: ID! class: String! hashCode: Int! headline: String! tagline: String! " +
                    "new(prefix: String!): String! }\n" +
                    "extend type Query { parcel: Parcel }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [RENAME_CLASH])
    fun clashSchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Slip { title: String! }\n" +
                    "extend type Query { slip: Slip }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [RENAME_COORDINATES])
    fun coordinatesSchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Parcel { id: ID! class: String! }\n" +
                    "extend type Query { parcel: Parcel default: String! }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [RENAME_PREFIXES])
    fun prefixesSchema() = TypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Parcel { id: ID! author: String! active: Boolean! }\n" +
                    "extend type Query { parcel: Parcel stamp: String! }\n"
            )
        )
    }
}

@GraphQLType
class Parcel {

    val id: String = "1"

    @GraphQLField("headline")
    val title: String = "Fragile"

    @get:GraphQLField("tagline")
    val subtitle: String = "Handle with care"

    @GraphQLField("class")
    fun kind(): String = "parcel"

    @GraphQLField("hashCode")
    fun digest(): Int = 42

    @GraphQLField("new")
    fun create(prefix: String): String = "$prefix parcel"
}

@Requires(env = [RENAME_MEMBERS])
@Singleton
@GraphQLRootResolver
open class ParcelQuery {

    open fun parcel(): Parcel = Parcel()
}

@GraphQLType
class Slip {

    val title: String = "title"

    @GraphQLField("title")
    fun heading(): String = "heading"
}

@Requires(env = [RENAME_CLASH])
@Singleton
@GraphQLRootResolver
open class SlipQuery {

    open fun slip(): Slip = Slip()
}

@GraphQLType
class CoordinateParcel {
    val id: String = "1"
}

@Requires(env = [RENAME_COORDINATES])
@Singleton
@GraphQLRootResolver
open class CoordinateQuery {

    @GraphQLSchemaCoordinate("Query.parcel")
    open fun fetchParcel(): CoordinateParcel = CoordinateParcel()

    @GraphQLSchemaCoordinate("Query.default")
    open fun defaultValue(): String = "plain"
}

@Requires(env = [RENAME_COORDINATES])
@Singleton
@GraphQLTypeResolver(CoordinateParcel::class)
open class CoordinateParcelResolver {

    @GraphQLSchemaCoordinate("Parcel.class")
    open fun kind(parcel: CoordinateParcel): String = "parcel"
}

@GraphQLType
class PrefixParcel {
    val id: String = "1"
}

@Requires(env = [RENAME_PREFIXES])
@Singleton
@GraphQLRootResolver
open class PrefixQuery {

    open fun parcel(): PrefixParcel = PrefixParcel()

    open fun getStamp(): String = "stamped"
}

@Requires(env = [RENAME_PREFIXES])
@Singleton
@GraphQLTypeResolver(PrefixParcel::class)
open class PrefixParcelResolver {

    open fun getAuthor(parcel: PrefixParcel): String = "Jane"

    open fun isActive(parcel: PrefixParcel): Boolean = true
}
