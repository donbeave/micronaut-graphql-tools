package example.directives

import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.exceptions.DuplicateDirectiveWiringException
import io.micronaut.graphql.tools.exceptions.UnwiredDirectivesException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectiveStartupTest {

    @Test
    fun aDirectiveWiredByANamedAndAnEveryElementSchemaDirectiveFailsTheStartup() {
        val e = assertInstanceOf(DuplicateDirectiveWiringException::class.java, failure(emptyMap(), DirectiveFixtures.CONFLICT))

        assertEquals(listOf("upper"), e.directiveNames)
        assertTrue(
            e.message!!.startsWith("[duplicate-directive-wiring] Directive @upper is wired by both a named and an every-element SchemaDirective bean"),
            e.message,
        )
    }

    @Test
    fun anAppliedDirectiveWithoutAWiringFailsTheStartupByDefault() {
        val e = assertInstanceOf(UnwiredDirectivesException::class.java, failure(emptyMap(), DirectiveFixtures.UNWIRED))

        assertEquals(listOf("legacy"), e.unwiredDirectives)
        assertTrue(e.message!!.startsWith("[unwired-directive] The schema applies directives that have no wiring: @legacy (Book)"), e.message)
    }

    @Test
    fun unwiredDirectivesIgnoreSkipsTheCheck() {
        start(mapOf(UNWIRED_DIRECTIVES to "ignore"), DirectiveFixtures.UNWIRED).use {
            assertNotNull(it.getBean(GraphQLSchema::class.java))
            assertFalse(manifestLines(it).contains("!unwired-directive legacy"), manifestLines(it).toString())
        }
    }

    @Test
    fun unwiredDirectivesWarnStartsAndRecordsTheDirectiveInTheManifest() {
        start(mapOf(UNWIRED_DIRECTIVES to "warn"), DirectiveFixtures.UNWIRED).use {
            assertNotNull(it.getBean(GraphQLSchema::class.java))
            assertTrue(manifestLines(it).contains("!unwired-directive legacy"), manifestLines(it).toString())
        }
    }

    @Test
    fun unwiredDirectivesFailFailsTheStartupEvenWithStrictOff() {
        assertInstanceOf(
            UnwiredDirectivesException::class.java,
            failure(mapOf(UNWIRED_DIRECTIVES to "fail", STRICT to false), DirectiveFixtures.UNWIRED),
        )
    }

    @Test
    fun strictOffAloneStartsAndRecordsTheDirectiveInTheManifest() {
        start(mapOf(STRICT to false), DirectiveFixtures.UNWIRED).use {
            assertTrue(manifestLines(it).contains("!unwired-directive legacy"), manifestLines(it).toString())
        }
    }

    @Test
    fun anInertDirectiveStartsWithoutAWiring() {
        start(mapOf(INERT_DIRECTIVES to listOf("legacy")), DirectiveFixtures.UNWIRED).use {
            assertNotNull(it.getBean(GraphQLSchema::class.java))
            assertFalse(manifestLines(it).contains("!unwired-directive legacy"), manifestLines(it).toString())
        }
    }

    @Test
    fun aSchemaLevelDirectiveThatNoSchemaCustomizerIsListedForFailsTheStartup() {
        val e = assertInstanceOf(
            UnwiredDirectivesException::class.java,
            failure(mapOf(SCHEMA_CUSTOMIZER_DIRECTIVES to listOf("other")), DirectiveFixtures.SCHEMA_UNLISTED),
        )

        assertEquals(listOf("audit"), e.unwiredDirectives)
        assertTrue(e.message!!.contains("@audit (schema definition)"), e.message)
    }

    @Test
    fun everyDirectiveOfASchemaLevelListStartsTheServer() {
        start(mapOf(SCHEMA_CUSTOMIZER_DIRECTIVES to listOf("audit", "other")), DirectiveFixtures.SCHEMA_UNLISTED).use {
            assertNotNull(it.getBean(GraphQLSchema::class.java).getSchemaAppliedDirective("other"))
        }
    }

    private fun failure(properties: Map<String, Any>, environment: String): Throwable {
        var cause: Throwable = assertThrows(BeanInstantiationException::class.java) { start(properties, environment).close() }
        while (cause !is DuplicateDirectiveWiringException && cause !is UnwiredDirectivesException && cause.cause != null) {
            cause = cause.cause!!
        }
        return cause
    }

    private fun start(properties: Map<String, Any>, environment: String): ApplicationContext =
        ApplicationContext.run(EmbeddedServer::class.java, properties + ("micronaut.server.port" to -1), environment).applicationContext

    private fun manifestLines(context: ApplicationContext): List<String> = context.getBean(BindingManifest::class.java).lines

    private companion object {
        const val UNWIRED_DIRECTIVES = "micronaut.graphql.tools.checks.unwired-directive"
        const val STRICT = "micronaut.graphql.tools.strict"
        const val INERT_DIRECTIVES = "micronaut.graphql.tools.allow.inert-directives"
        const val SCHEMA_CUSTOMIZER_DIRECTIVES = "micronaut.graphql.tools.allow.schema-customizer-directives"
    }
}
