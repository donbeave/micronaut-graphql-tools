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
package example.directives

import graphql.schema.GraphQLSchema
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class DirectiveWiringSpec extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    @Inject
    GraphQLSchema schema

    private Map<String, Object> data(String query) {
        Map<String, Object> response = client.toBlocking().retrieve(
                HttpRequest.POST('/graphql', [query: query]),
                Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT))
        assert response.errors == null: "GraphQL errors: ${response.errors}"
        (Map<String, Object>) response.data
    }

    def 'a named SchemaDirective runs on the field that applies it'() {
        expect:
        data('{ shout }').shout == 'QUIET'
    }

    def 'an every-element SchemaDirective runs on the field that applies it'() {
        expect:
        data('{ tagged }').tagged == '[t] value'
    }

    def 'a wiring does not change a field that does not apply its directive'() {
        expect:
        data('{ introduced }').introduced == 'introduced introduced'
    }

    def 'a schema-level directive is handled by the schema customizer that it is listed for'() {
        expect:
        schema.getSchemaAppliedDirective('audit') != null
        ((Map) data('{ __schema { description } }').__schema).description == 'audited'
    }
}
