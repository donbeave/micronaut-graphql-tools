package io.micronaut.graphql.tools.visitor

import graphql.GraphQL
import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.exceptions.SchemaBindingException

/**
 * Starts the application of a compiled case and reports the startup failure as {@code code: message}, or {@code bound}.
 * It is loaded by the class loader of the application, so it answers in JDK types only.
 */
@CompileStatic
final class StartupProbe {

    private StartupProbe() {
    }

    static String start(String schemaDir) {
        ApplicationContext context = null
        try {
            context = ApplicationContext.builder(['micronaut.graphql.tools.schema.locations': ["file:${schemaDir}/*.graphqls".toString()],
                                                  'micronaut.graphql.tools.eager-init': false] as Map<String, Object>).start()
            context.getBean(GraphQL)
            return 'bound'
        } catch (RuntimeException e) {
            Throwable known = null
            for (Throwable t = e; t != null && known == null; t = t.cause) {
                known = t instanceof SchemaBindingException ? t : null
            }
            return known == null ? e.toString() : "${((SchemaBindingException) known).code().code()}: ${known.message}".toString()
        } finally {
            context?.close()
        }
    }
}
