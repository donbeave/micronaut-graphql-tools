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
import groovy.transform.CompileStatic
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

// The resolvers of an application that came from graphql-java-tools, written with the annotations of this library.
@CompileStatic
@Factory
class MigrationFixtures {

    static final String MIGRATION = 'migration'
    // A resolver method named getX, which binds a field only with resolver-accessor-prefixes
    static final String PREFIXES = 'migration-prefixes'
    // A field whose name is not a Java identifier style name
    static final String SNAKE = 'migration-snake'
    // The same field on a class with no @GraphQLField
    static final String SNAKE_PLAIN = 'migration-snake-plain'
    // A schema type that no field reaches, which a check reports
    static final String FINDINGS = 'migration-findings'
    // A field argument that a resolver declares as an Optional
    static final String OPTIONAL_ARGUMENT = 'migration-optional-argument'
    // An object type whose source is a Map
    static final String MAP = 'migration-map'

    private static GraphQLTypeDefinitionRegistryCustomizer schema(String sdl) {
        return { registry -> registry.merge(new SchemaParser().parse(sdl)) } as GraphQLTypeDefinitionRegistryCustomizer
    }

    @Bean
    @Singleton
    @Requires(env = MigrationFixtures.MIGRATION)
    GraphQLTypeDefinitionRegistryCustomizer noteSchema() {
        return schema('type Note { id: ID! title: String! done: Boolean! summary: String! }\n' +
                'extend type Query { note(id: ID!): Note notes(tag: String, limit: Int!): [Note!]! }\n' +
                'extend type Mutation { addNote(title: String!): Note! }\n')
    }

    @Bean
    @Singleton
    @Requires(env = MigrationFixtures.PREFIXES)
    GraphQLTypeDefinitionRegistryCustomizer labelSchema() {
        return schema('type Label { text: String! shout: String! }\n' +
                'extend type Query { label: Label }\n')
    }

    @Bean
    @Singleton
    @Requires(env = MigrationFixtures.SNAKE)
    GraphQLTypeDefinitionRegistryCustomizer stampSchema() {
        return schema('type Stamp { created_at: String! }\n' +
                'extend type Query { stamp: Stamp }\n')
    }

    @Bean
    @Singleton
    @Requires(env = MigrationFixtures.SNAKE_PLAIN)
    GraphQLTypeDefinitionRegistryCustomizer plainStampSchema() {
        return schema('type PlainStamp { created_at: String! }\n' +
                'extend type Query { plainStamp: PlainStamp }\n')
    }

    @Bean
    @Singleton
    @Requires(env = MigrationFixtures.MAP)
    GraphQLTypeDefinitionRegistryCustomizer mapSchema() {
        return schema('type Bag { size: Int! }\n' +
                'extend type Query { bag: Bag }\n')
    }

    @Bean
    @Singleton
    @Requires(env = MigrationFixtures.FINDINGS)
    GraphQLTypeDefinitionRegistryCustomizer findingsSchema() {
        return schema('type Orphan { id: ID! }\n' +
                'extend type Query { ping: String! }\n')
    }

    @Bean
    @Singleton
    @Requires(env = MigrationFixtures.OPTIONAL_ARGUMENT)
    GraphQLTypeDefinitionRegistryCustomizer greetSchema() {
        return schema('extend type Query { greet(name: String): String }\n')
    }

}

// tag::model[]
@CompileStatic
@GraphQLType
class Note {
    String id
    String title
    boolean done
}
// end::model[]

// tag::query[]
@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.MIGRATION)
@GraphQLRootResolver
class NoteQuery {

    private final List<Note> notes = [
            new Note(id: '1', title: 'first', done: false),
            new Note(id: '2', title: 'second', done: true)
    ]

    // an empty Optional is null, which the nullable field note accepts
    Optional<Note> note(String id) {
        return Optional.ofNullable(notes.find { it.id == id })
    }

    // the parameters bind by name, so their order is not the order of the arguments in the schema, and an
    // ArgumentValue is the counterpart of an Optional argument
    List<Note> notes(int limit, ArgumentValue<String> tag) {
        return notes.findAll { Note note -> tag.asOptional().map { String it -> note.title.contains(it) }.orElse(true) }
                .take(limit)
    }

}
// end::query[]

// tag::mutation[]
@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.MIGRATION)
@GraphQLRootResolver
class NoteMutation {

    Note addNote(String title) {
        return new Note(id: '3', title: title, done: false)
    }

}
// end::mutation[]

// tag::type-resolver[]
@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.MIGRATION)
@GraphQLTypeResolver(Note)
class NoteFields {

    String summary(Note note) {
        return note.title + (note.done ? ' (done)' : '')
    }

}
// end::type-resolver[]

@CompileStatic
@GraphQLType
class Label {
    String text = 'note'
}

@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.PREFIXES)
@GraphQLRootResolver
class LabelQuery {

    Label label() {
        return new Label()
    }

}

// tag::accessor-prefix[]
@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.PREFIXES)
@GraphQLTypeResolver(Label)
class LabelFields {

    String getShout(Label label) {
        return label.text.toUpperCase()
    }

}
// end::accessor-prefix[]

// tag::renamed-field[]
@CompileStatic
@GraphQLType
class Stamp {

    @GraphQLField('created_at')
    String createdAt = 'today'

}
// end::renamed-field[]

@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.SNAKE)
@GraphQLRootResolver
class StampQuery {

    Stamp stamp() {
        return new Stamp()
    }

}

@CompileStatic
@GraphQLType
class PlainStamp {
    String createdAt = 'today'
}

@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.SNAKE_PLAIN)
@GraphQLRootResolver
class PlainStampQuery {

    PlainStamp plainStamp() {
        return new PlainStamp()
    }

}

@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.MAP)
@GraphQLRootResolver
class BagQuery {

    Map<String, Object> bag() {
        return [size: 3] as Map<String, Object>
    }

}

@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.FINDINGS)
@GraphQLRootResolver
class PingQuery {

    String ping() {
        return 'pong'
    }

}

@CompileStatic
@Singleton
@Requires(env = MigrationFixtures.OPTIONAL_ARGUMENT)
@GraphQLRootResolver
class GreetQuery {

    String greet(Optional<String> name) {
        return 'hello ' + name.orElse('nobody')
    }

}
