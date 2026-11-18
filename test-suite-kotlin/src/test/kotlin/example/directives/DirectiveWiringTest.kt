package example.directives

import graphql.schema.GraphQLSchema
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@MicronautTest
class DirectiveWiringTest(
    @Client("/") private val client: HttpClient,
    private val schema: GraphQLSchema,
) {

    @Suppress("UNCHECKED_CAST")
    private fun data(query: String): Map<String, Any?> {
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/graphql", mapOf("query" to query)),
            Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT),
        )
        assertNull(response["errors"], "GraphQL errors: ${response["errors"]}")
        return response["data"] as Map<String, Any?>
    }

    @Test
    fun aNamedSchemaDirectiveRunsOnTheFieldThatAppliesIt() {
        assertEquals("QUIET", data("{ shout }")["shout"])
    }

    @Test
    fun anEveryElementSchemaDirectiveRunsOnTheFieldThatAppliesIt() {
        assertEquals("[t] value", data("{ tagged }")["tagged"])
    }

    @Test
    fun aWiringDoesNotChangeAFieldThatDoesNotApplyItsDirective() {
        assertEquals("introduced introduced", data("{ introduced }")["introduced"])
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun aSchemaLevelDirectiveIsHandledByTheSchemaCustomizerThatItIsListedFor() {
        assertNotNull(schema.getSchemaAppliedDirective("audit"))
        assertEquals("audited", (data("{ __schema { description } }")["__schema"] as Map<String, Any?>)["description"])
    }
}
