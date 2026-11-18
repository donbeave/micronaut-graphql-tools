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
