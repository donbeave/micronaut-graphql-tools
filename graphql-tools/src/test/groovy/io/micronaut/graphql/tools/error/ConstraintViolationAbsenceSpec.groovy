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
package io.micronaut.graphql.tools.error

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.BeanDefinition
import jakarta.validation.ConstraintViolationException
import spock.lang.Specification

/**
 * The mapper of constraint violations is a bean only when Jakarta Validation is on the classpath, so an application without it
 * pays nothing for it: no bean to create, no mapper in the lookup of an exception class (#231).
 */
class ConstraintViolationAbsenceSpec extends Specification {

    private static final String MAPPER = 'io.micronaut.graphql.tools.internal.error.ConstraintViolationExceptionMapper'

    void "the mapper bean requires the Jakarta Validation class and is one of the mappers when it is present"() {
        given:
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse('schema { query: Query } type Query { ok: String }'))
        ApplicationContext context = ApplicationContext
                .builder(['spec.name': 'ConstraintViolationAbsenceSpec', 'micronaut.graphql.tools.eager-init': false], 'test')
                .singletons(registry).start()

        when:
        BeanDefinition<?> definition = context.getBeanDefinitions(GraphQLExceptionMapper).find { it.beanType.name == MAPPER }

        then:
        definition != null
        definition.getAnnotation(Requires).classValues('classes') as List == [ConstraintViolationException]

        cleanup:
        context?.close()
    }
}
