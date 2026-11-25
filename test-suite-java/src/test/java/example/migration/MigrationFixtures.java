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
package example.migration;

import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.input.ArgumentValue;
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// The resolvers of an application that came from graphql-java-tools, written with the annotations and contracts of this
// library.
@Factory
public class MigrationFixtures {

    public static final String MIGRATION = "migration";
    // A resolver method named getX, which binds a field only with resolver-accessor-prefixes
    public static final String PREFIXES = "migration-prefixes";
    // A field whose name is not a Java identifier style name
    public static final String SNAKE = "migration-snake";
    // The same field on a class with no @GraphQLField
    public static final String SNAKE_PLAIN = "migration-snake-plain";
    // A schema type that no field reaches, which a check reports
    public static final String FINDINGS = "migration-findings";
    // A field argument that a resolver declares as an Optional
    public static final String OPTIONAL_ARGUMENT = "migration-optional-argument";
    // An object type whose source is a Map
    public static final String MAP = "migration-map";

    @Bean
    @Singleton
    @Requires(env = MIGRATION)
    GraphQLTypeDefinitionRegistryCustomizer noteSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Note { id: ID! title: String! done: Boolean! summary: String! }\n"
                        + "extend type Query { note(id: ID!): Note notes(tag: String, limit: Int!): [Note!]! }\n"
                        + "extend type Mutation { addNote(title: String!): Note! }\n"));
    }

    @Bean
    @Singleton
    @Requires(env = PREFIXES)
    GraphQLTypeDefinitionRegistryCustomizer labelSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Label { text: String! shout: String! }\n"
                        + "extend type Query { label: Label }\n"));
    }

    @Bean
    @Singleton
    @Requires(env = SNAKE)
    GraphQLTypeDefinitionRegistryCustomizer stampSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Stamp { created_at: String! }\n"
                        + "extend type Query { stamp: Stamp }\n"));
    }

    // tag::model[]
    @GraphQLType
    public static class Note {

        private final String id;
        private final String title;
        private final boolean done;

        public Note(String id, String title, boolean done) {
            this.id = id;
            this.title = title;
            this.done = done;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public boolean isDone() {
            return done;
        }

    }
    // end::model[]

    // tag::query[]
    @Singleton
    @Requires(env = MIGRATION)
    @GraphQLRootResolver
    public static class NoteQuery {

        private final List<Note> notes = new ArrayList<>(List.of(new Note("1", "first", false), new Note("2", "second", true)));

        // an empty Optional is null, which the nullable field note accepts
        public Optional<Note> note(String id) {
            return notes.stream().filter(note -> note.getId().equals(id)).findFirst();
        }

        // the parameters bind by name, so their order is not the order of the arguments in the schema, and an
        // ArgumentValue is the counterpart of an Optional argument
        public List<Note> notes(int limit, ArgumentValue<String> tag) {
            return notes.stream()
                    .filter(note -> tag.asOptional().map(note.getTitle()::contains).orElse(true))
                    .limit(limit)
                    .toList();
        }

    }
    // end::query[]

    // tag::mutation[]
    @Singleton
    @Requires(env = MIGRATION)
    @GraphQLRootResolver
    public static class NoteMutation {

        public Note addNote(String title) {
            return new Note("3", title, false);
        }

    }
    // end::mutation[]

    // tag::type-resolver[]
    @Singleton
    @Requires(env = MIGRATION)
    @GraphQLTypeResolver(Note.class)
    public static class NoteFields {

        public String summary(Note note) {
            return note.getTitle() + (note.isDone() ? " (done)" : "");
        }

    }
    // end::type-resolver[]

    @GraphQLType
    public static class Label {

        public String getText() {
            return "note";
        }

    }

    @Singleton
    @Requires(env = PREFIXES)
    @GraphQLRootResolver
    public static class LabelQuery {

        public Label label() {
            return new Label();
        }

    }

    // tag::accessor-prefix[]
    @Singleton
    @Requires(env = PREFIXES)
    @GraphQLTypeResolver(Label.class)
    public static class LabelFields {

        public String getShout(Label label) {
            return label.getText().toUpperCase();
        }

    }
    // end::accessor-prefix[]

    // tag::renamed-field[]
    @GraphQLType
    public static class Stamp {

        @GraphQLField("created_at")
        public String getCreatedAt() {
            return "today";
        }

    }
    // end::renamed-field[]

    @Singleton
    @Requires(env = SNAKE)
    @GraphQLRootResolver
    public static class StampQuery {

        public Stamp stamp() {
            return new Stamp();
        }

    }

    @Bean
    @Singleton
    @Requires(env = SNAKE_PLAIN)
    GraphQLTypeDefinitionRegistryCustomizer plainStampSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type PlainStamp { created_at: String! }\n"
                        + "extend type Query { plainStamp: PlainStamp }\n"));
    }

    @GraphQLType
    public static class PlainStamp {

        public String getCreatedAt() {
            return "today";
        }

    }

    @Singleton
    @Requires(env = SNAKE_PLAIN)
    @GraphQLRootResolver
    public static class PlainStampQuery {

        public PlainStamp plainStamp() {
            return new PlainStamp();
        }

    }

    @Bean
    @Singleton
    @Requires(env = MAP)
    GraphQLTypeDefinitionRegistryCustomizer mapSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Bag { size: Int! }\n"
                        + "extend type Query { bag: Bag }\n"));
    }

    @Singleton
    @Requires(env = MAP)
    @GraphQLRootResolver
    public static class BagQuery {

        public java.util.Map<String, Object> bag() {
            return java.util.Map.of("size", 3);
        }

    }

    @Bean
    @Singleton
    @Requires(env = OPTIONAL_ARGUMENT)
    GraphQLTypeDefinitionRegistryCustomizer greetSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "extend type Query { greet(name: String): String }\n"));
    }

    @Singleton
    @Requires(env = OPTIONAL_ARGUMENT)
    @GraphQLRootResolver
    public static class GreetQuery {

        public String greet(Optional<String> name) {
            return "hello " + name.orElse("nobody");
        }

    }

    @Bean
    @Singleton
    @Requires(env = FINDINGS)
    GraphQLTypeDefinitionRegistryCustomizer findingsSchema() {
        return registry -> registry.merge(new SchemaParser().parse(
                "type Orphan { id: ID! }\n"
                        + "extend type Query { ping: String! }\n"));
    }

    @Singleton
    @Requires(env = FINDINGS)
    @GraphQLRootResolver
    public static class PingQuery {

        public String ping() {
            return "pong";
        }

    }

}
