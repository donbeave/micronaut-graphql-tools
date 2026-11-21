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
package example.eager

import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.io.ResourceResolver
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

@Factory
@Requires(env = 'broken-binding')
class BrokenBindingFactory {

    @Bean
    @Singleton
    @Replaces(TypeDefinitionRegistry)
    TypeDefinitionRegistry brokenTypeDefinitionRegistry(ResourceResolver resourceResolver) {
        SchemaParser parser = new SchemaParser()
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(parser.parse(resourceResolver.getResourceAsStream('classpath:schema.graphqls').orElseThrow().newReader()))
        registry.merge(parser.parse('extend type Query { unbound: String }'))
        registry
    }
}

class EagerBindingSpec extends Specification {

    static final Map<String, Object> PORT = ['micronaut.server.port': -1]

    def 'a valid schema starts the server with the schema already bound'() {
        when:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, PORT)

        then:
        server.applicationContext.getBean(GraphQLSchema).queryType.getFieldDefinition('allBooks') != null

        cleanup:
        server?.close()
    }

    def 'a broken binding fails the server start'() {
        when:
        ApplicationContext.run(EmbeddedServer, PORT, 'broken-binding')

        then:
        BeanInstantiationException e = thrown()
        rootCause(e).message.contains('Query.unbound')
    }

    private static Throwable rootCause(Throwable e) {
        while (e.cause != null && !e.cause.is(e)) {
            e = e.cause
        }
        e
    }
}
