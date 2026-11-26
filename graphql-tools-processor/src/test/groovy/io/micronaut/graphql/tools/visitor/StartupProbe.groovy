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
            return known == null ? e.toString() : "${((SchemaBindingException) known).getCode().getCode()}: ${known.message}".toString()
        } finally {
            context?.close()
        }
    }
}
