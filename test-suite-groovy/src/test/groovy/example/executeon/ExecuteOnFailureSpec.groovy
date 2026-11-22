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
package example.executeon

import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import spock.lang.Specification

// The Groovy suite runs on the JVM: the startup failures of @ExecuteOn.
class ExecuteOnFailureSpec extends Specification {

    private static Throwable failure(String environment) {
        try {
            ApplicationContext context = ApplicationContext.run([:], environment)
            try {
                context.getBean(GraphQLSchema)
            } finally {
                context.close()
            }
            return null
        } catch (Exception e) {
            Throwable cause = e
            while (!(cause instanceof SchemaBindingException) && cause.cause != null && !cause.cause.is(cause)) {
                cause = cause.cause
            }
            return cause
        }
    }

    void 'an unknown executor fails the startup and lists the executor beans'() {
        when:
        SchemaBindingException e = (SchemaBindingException) failure(ExecuteOnFixtures.UNKNOWN)

        then:
        e.getCode() == BindingCode.UNKNOWN_EXECUTOR
        e.message.startsWith('[unknown-executor] ')
        e.message.contains('@ExecuteOn("no-such-executor")')
        e.message.contains('Query.slowLookup')
        e.message.contains('The executor beans are:') && e.message.contains('io')
        e.message.contains('micronaut.executors')
    }

    void '@ExecuteOn on a property fails the startup and says how to fix it'() {
        when:
        SchemaBindingException e = (SchemaBindingException) failure(ExecuteOnFixtures.PROPERTY)

        then:
        e.getCode() == BindingCode.EXECUTE_ON_PROPERTY
        e.message.startsWith('[execute-on-property] ')
        e.message.contains('@GraphQLField method') && e.message.contains('type resolver')
    }
}
