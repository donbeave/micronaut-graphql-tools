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

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.manifest.BindingManifest
import spock.lang.Specification

// The Groovy suite runs on the JVM: the mappings that the migration page lists for resolvers written for
// graphql-java-tools.
class MigrationTest extends Specification {

    private static ExecutionResult execute(Map<String, Object> properties, String environment, String query) {
        ApplicationContext context = ApplicationContext.run(properties, environment)
        try {
            return GraphQL.newGraphQL(context.getBean(GraphQLSchema)).build().execute(query)
        } finally {
            context.close()
        }
    }

    private static Map<String, Object> data(Map<String, Object> properties, String environment, String query) {
        ExecutionResult result = execute(properties, environment, query)
        assert result.errors.isEmpty(): result.errors.toString()
        return result.getData()
    }

    private static String failureCode(Map<String, Object> properties, String environment) {
        try {
            execute(properties, environment, '{ __typename }')
            return null
        } catch (Exception e) {
            Throwable cause = e
            while (!(cause instanceof SchemaBindingException) && cause.cause != null && !cause.cause.is(cause)) {
                cause = cause.cause
            }
            assert cause instanceof SchemaBindingException: cause.toString()
            String message = cause.message
            return message.substring(1, message.indexOf(']'))
        }
    }

    void 'root resolvers bind by method name and the model by its properties'() {
        when:
        Map<String, Object> data = data([:], MigrationFixtures.MIGRATION,
                '{ note(id: "1") { id title done summary } missing: note(id: "9") { id } }')

        then:
        data.note == [id: '1', title: 'first', done: false, summary: 'first']
        data.missing == null
    }

    void 'a type resolver method binds a field the model does not have'() {
        expect:
        data([:], MigrationFixtures.MIGRATION, 'mutation { addNote(title: "third") { summary } }') ==
                [addNote: [summary: 'third']]
    }

    void 'arguments bind by name not by position'() {
        expect:
        // the schema declares (tag, limit) and the method (limit, tag)
        data([:], MigrationFixtures.MIGRATION, '{ notes(tag: "sec", limit: 5) { id } }') == [notes: [[id: '2']]]
        data([:], MigrationFixtures.MIGRATION, '{ notes(limit: 1) { id } }') == [notes: [[id: '1']]]
    }

    void 'a method named getX binds a field only with resolver accessor prefixes'() {
        expect:
        failureCode([:], MigrationFixtures.PREFIXES) == 'field-not-bound'
        data(['micronaut.graphql.tools.resolver-accessor-prefixes': true], MigrationFixtures.PREFIXES,
                '{ label { text shout } }') == [label: [text: 'note', shout: 'NOTE']]
    }

    void 'a field with an underscore needs an explicit name'() {
        expect:
        failureCode([:], MigrationFixtures.SNAKE_PLAIN) == 'field-not-bound'
        data([:], MigrationFixtures.SNAKE, '{ stamp { created_at } }') == [stamp: [created_at: 'today']]
    }

    void 'an Optional argument is refused at startup'() {
        expect:
        failureCode([:], MigrationFixtures.OPTIONAL_ARGUMENT) == 'incorrect-class-mapping'
    }

    void 'a Map is not the source of an object type'() {
        expect:
        failureCode([:], MigrationFixtures.MAP) == 'implementation-not-found'
    }

    // tag::recipe[]
    void 'the migration settings turn a failure into a finding in the manifest'() {
        given:
        Map<String, Object> settings = [
                'micronaut.graphql.tools.strict'           : false,
                'micronaut.graphql.tools.precedence'       : 'resolver-first',
                'micronaut.graphql.tools.manifest.enabled' : true
        ]

        expect:
        failureCode([:], MigrationFixtures.FINDINGS) == 'unreachable-type'

        when:
        ApplicationContext context = ApplicationContext.run(settings, MigrationFixtures.FINDINGS)
        List<String> lines
        try {
            context.getBean(GraphQLSchema)
            lines = context.getBean(BindingManifest).lines
        } finally {
            context.close()
        }

        then:
        lines.any { it.startsWith('!unreachable-type ') }
    }
    // end::recipe[]

}
