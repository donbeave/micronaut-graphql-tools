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
package example.migration

import graphql.schema.idl.SchemaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLField
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.input.ArgumentValue
import jakarta.inject.Singleton
import java.util.Optional

const val MIGRATION = "migration"

// A resolver method named getX, which binds a field only with resolver-accessor-prefixes
const val PREFIXES = "migration-prefixes"

// A field whose name is not a Java identifier style name
const val SNAKE = "migration-snake"

// The same field on a class with no @GraphQLField
const val SNAKE_PLAIN = "migration-snake-plain"

// A schema type that no field reaches, which a check reports
const val FINDINGS = "migration-findings"

// A field argument that a resolver declares as an Optional
const val OPTIONAL_ARGUMENT = "migration-optional-argument"

// An object type whose source is a Map
const val MAP = "migration-map"

// The resolvers of an application that came from graphql-java-tools, written with the annotations of this library.
@Factory
class MigrationFixtures {

    @Bean
    @Singleton
    @Requires(env = [MIGRATION])
    fun noteSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Note { id: ID! title: String! done: Boolean! summary: String! }\n" +
                    "extend type Query { note(id: ID!): Note notes(tag: String, limit: Int!): [Note!]! }\n" +
                    "extend type Mutation { addNote(title: String!): Note! }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [PREFIXES])
    fun labelSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Label { text: String! shout: String! }\n" +
                    "extend type Query { label: Label }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [SNAKE])
    fun stampSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Stamp { created_at: String! }\n" +
                    "extend type Query { stamp: Stamp }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [SNAKE_PLAIN])
    fun plainStampSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type PlainStamp { created_at: String! }\n" +
                    "extend type Query { plainStamp: PlainStamp }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [MAP])
    fun mapSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Bag { size: Int! }\n" +
                    "extend type Query { bag: Bag }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [FINDINGS])
    fun findingsSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(
            SchemaParser().parse(
                "type Orphan { id: ID! }\n" +
                    "extend type Query { ping: String! }\n"
            )
        )
    }

    @Bean
    @Singleton
    @Requires(env = [OPTIONAL_ARGUMENT])
    fun greetSchema() = GraphQLTypeDefinitionRegistryCustomizer { registry ->
        registry.merge(SchemaParser().parse("extend type Query { greet(name: String): String }\n"))
    }
}

// tag::model[]
@GraphQLType
class Note(val id: String, val title: String, val done: Boolean)
// end::model[]

// tag::query[]
@Singleton
@Requires(env = [MIGRATION])
@GraphQLRootResolver
open class NoteQuery {

    private val notes = listOf(Note("1", "first", false), Note("2", "second", true))

    // an empty Optional is null, which the nullable field note accepts
    open fun note(id: String): Optional<Note> = Optional.ofNullable(notes.firstOrNull { it.id == id })

    // the parameters bind by name, so their order is not the order of the arguments in the schema, and an
    // ArgumentValue is the counterpart of an Optional argument
    open fun notes(limit: Int, tag: ArgumentValue<String>): List<Note> =
        notes.filter { note -> tag.asOptional().map { note.title.contains(it) }.orElse(true) }.take(limit)
}
// end::query[]

// tag::mutation[]
@Singleton
@Requires(env = [MIGRATION])
@GraphQLRootResolver
open class NoteMutation {

    open fun addNote(title: String): Note = Note("3", title, false)
}
// end::mutation[]

// tag::type-resolver[]
@Singleton
@Requires(env = [MIGRATION])
@GraphQLTypeResolver(Note::class)
open class NoteFields {

    open fun summary(note: Note): String = note.title + if (note.done) " (done)" else ""
}
// end::type-resolver[]

@GraphQLType
class Label {
    val text: String = "note"
}

@Singleton
@Requires(env = [PREFIXES])
@GraphQLRootResolver
open class LabelQuery {

    open fun label(): Label = Label()
}

// tag::accessor-prefix[]
@Singleton
@Requires(env = [PREFIXES])
@GraphQLTypeResolver(Label::class)
open class LabelFields {

    open fun getShout(label: Label): String = label.text.uppercase()
}
// end::accessor-prefix[]

// tag::renamed-field[]
@GraphQLType
class Stamp {

    @GraphQLField("created_at")
    val createdAt: String = "today"
}
// end::renamed-field[]

@Singleton
@Requires(env = [SNAKE])
@GraphQLRootResolver
open class StampQuery {

    open fun stamp(): Stamp = Stamp()
}

@GraphQLType
class PlainStamp {
    val createdAt: String = "today"
}

@Singleton
@Requires(env = [SNAKE_PLAIN])
@GraphQLRootResolver
open class PlainStampQuery {

    open fun plainStamp(): PlainStamp = PlainStamp()
}

@Singleton
@Requires(env = [MAP])
@GraphQLRootResolver
open class BagQuery {

    open fun bag(): Map<String, Any> = mapOf("size" to 3)
}

@Singleton
@Requires(env = [FINDINGS])
@GraphQLRootResolver
open class PingQuery {

    open fun ping(): String = "pong"
}

@Singleton
@Requires(env = [OPTIONAL_ARGUMENT])
@GraphQLRootResolver
open class GreetQuery {

    open fun greet(name: Optional<String>): String = "hello " + name.orElse("nobody")
}
