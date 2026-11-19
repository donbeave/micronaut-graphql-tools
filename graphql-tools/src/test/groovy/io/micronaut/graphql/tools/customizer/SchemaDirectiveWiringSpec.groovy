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
package io.micronaut.graphql.tools.customizer

import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.validation.constraints.DirectiveConstraints
import graphql.validation.rules.OnValidationErrorStrategy
import graphql.validation.rules.ValidationRules
import graphql.validation.schemawiring.ValidationSchemaWiring
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.RuntimeWiringCustomizer
import io.micronaut.graphql.tools.SchemaDirective
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class SchemaDirectiveWiringSpec extends AbstractTest {

    static final String SPEC_NAME = "SchemaDirectiveWiringSpec"
    static final String SPEC_NAME_VALIDATION = "SchemaDirectiveWiringSpec.validation"
    static final String SPEC_NAME_DUPLICATE = "SchemaDirectiveWiringSpec.duplicate"
    static final String SPEC_NAME_NAMED_AND_EVERY_ELEMENT = "SchemaDirectiveWiringSpec.namedAndEveryElement"
    static final String SPEC_NAME_REPLACED = "SchemaDirectiveWiringSpec.replaced"
    static final String SPEC_NAME_WRAPPED_BEAN = "SchemaDirectiveWiringSpec.wrappedBean"
    static final String SPEC_NAME_UNWRAPPED_BEAN = "SchemaDirectiveWiringSpec.unwrappedBean"

    static final String SPEC_NAME_PROTOTYPE = "SchemaDirectiveWiringSpec.prototype"
    static final String SPEC_NAME_PROTOTYPE_UNWRAPPED = "SchemaDirectiveWiringSpec.prototypeUnwrapped"
    static final String SPEC_NAME_CUSTOMIZER_BEAN = "SchemaDirectiveWiringSpec.customizerBean"

    static final List<String> UPPER_CALLS = Collections.synchronizedList([])

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

directive @upper on FIELD_DEFINITION
directive @suffix(value: String!) on FIELD_DEFINITION

type Query {
  hello: String @upper
  plain: String
  decorated: String @suffix(value: "s")
}
"""

    void setup() {
        UPPER_CALLS.clear()
    }

    void "wirings for every element run lowest order first; a named SchemaDirective only where applied"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ hello plain decorated }')

        then: 'the wirings for every element wrap in @Order order, the lowest innermost, whatever the declaration order'
        result.errors.isEmpty()
        result.data == [hello: 'WORLD', plain: 'plain', decorated: 'decorated-first-second-third']

        and: 'graphql-java called the named wiring only for the field that applies @upper'
        UPPER_CALLS == ['hello']
    }

    void "a SchemaDirectiveWiring bean wrapped by a SchemaDirective is applied only through it"() {
        given:
        startContext(SCHEMA, SPEC_NAME_WRAPPED_BEAN)

        when:
        def result = executeQuery('{ hello plain }')

        then: 'not also for every element: plain stays lower case'
        result.errors.isEmpty()
        result.data == [hello: 'WORLD', plain: 'plain']
        UPPER_CALLS == ['hello']
    }

    void "a SchemaDirectiveWiring bean that no SchemaDirective wraps fails the startup and is named"() {
        given:
        startContext(SCHEMA, SPEC_NAME_UNWRAPPED_BEAN)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNWRAPPED_DIRECTIVE_WIRING
        e.cause.beans == ["${UpperWiring.name} (Definition: ${UnwrappedFactory.name})".toString()]
        e.cause.message == "[unwrapped-directive-wiring] SchemaDirectiveWiring beans that no SchemaDirective bean wraps: ${e.cause.beans[0]}.\n" +
                "  The library applies a wiring only through a SchemaDirective: wrap each one in " +
                "SchemaDirective.named(name, wiring) (called where the directive is applied) or " +
                "SchemaDirective.everyElement(names, wiring) (called for every schema element), injecting the bean " +
                "into the SchemaDirective's factory method, or remove the bean."
    }

    void "a @Prototype SchemaDirectiveWiring bean wrapped by a SchemaDirective starts and applies"() {
        given:
        startContext(SCHEMA, SPEC_NAME_PROTOTYPE)

        when:
        def result = executeQuery('{ hello plain }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'WORLD', plain: 'plain']
        UPPER_CALLS == ['hello']
    }

    void "a @Prototype SchemaDirectiveWiring bean that no SchemaDirective wraps still fails the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME_PROTOTYPE_UNWRAPPED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.UNWRAPPED_DIRECTIVE_WIRING
        e.cause.beans.size() == 1
        e.cause.beans[0].startsWith(PrototypeUpperWiring.name)
    }

    void "a SchemaDirectiveWiring bean that a RuntimeWiringCustomizer registers starts and applies"() {
        given:
        startContext(SCHEMA, SPEC_NAME_CUSTOMIZER_BEAN)

        when:
        def result = executeQuery('{ hello plain }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'WORLD', plain: 'plain']
        UPPER_CALLS == ['hello']
    }

    void "graphql-java-extended-validation: its ValidationSchemaWiring for every element, naming its constraint directives"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  greet(name: String @Size(min: 2, max: 5)): String
}
"""
        startContext(schema, SPEC_NAME_VALIDATION)

        when:
        def result = executeQuery('{ greet(name: "Ann") }')

        then:
        result.errors.isEmpty()
        result.data == [greet: 'Hello, Ann']

        when:
        result = executeQuery('{ greet(name: "Bartholomew") }')

        then: 'the resolver is not called'
        result.data == [greet: null]
        result.errors.size() == 1
        result.errors[0].message.contains('size must be between 2 and 5')
        result.errors[0].path == ['greet']
    }

    void "two SchemaDirective beans for one directive fail the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME_DUPLICATE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.DUPLICATE_DIRECTIVE_WIRING
        e.cause.message == "[duplicate-directive-wiring] More than one SchemaDirective bean wires directive @upper: ${UpperWiring.name}, ${UpperWiring.name}. Keep one.".toString()
    }

    void "a directive wired by a named and an every-element SchemaDirective fails the startup, naming both"() {
        given:
        startContext(SCHEMA, SPEC_NAME_NAMED_AND_EVERY_ELEMENT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.DUPLICATE_DIRECTIVE_WIRING
        e.cause.directiveNames == ['upper']
        e.cause.message == ("[duplicate-directive-wiring] Directive @upper is wired by both a named and an every-element SchemaDirective bean, "
                + "so it would take effect twice: named ${UpperWiring.name}, every element ${BangWiring.name}. "
                + "Use one of them: remove the named bean, or make the every-element wiring stop handling the directive and then drop its name.").toString()
    }

    void "a RuntimeWiringCustomizer may not replace the wiring of a SchemaDirective bean"() {
        given:
        startContext(SCHEMA, SPEC_NAME_REPLACED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.code() == BindingCode.RUNTIME_WIRING_OVERRIDE
        e.cause.replacements == ["directive wiring @upper (${ReplaceUpper.name})".toString()]
    }

    static class UpperWiring implements SchemaDirectiveWiring {
        @Override
        GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            UPPER_CALLS << environment.element.name
            DataFetcher<?> original = environment.fieldDataFetcher
            return environment.setFieldDataFetcher({ env -> original.get(env)?.toString()?.toUpperCase() } as DataFetcher)
        }
    }

    static class SuffixWiring implements SchemaDirectiveWiring {
        private final String name

        SuffixWiring(String name) {
            this.name = name
        }

        @Override
        GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            if (!environment.containsDirective('suffix')) {
                return environment.element
            }
            DataFetcher<?> original = environment.fieldDataFetcher
            return environment.setFieldDataFetcher({ env -> original.get(env).toString() + '-' + name } as DataFetcher)
        }
    }

    @Requires(property = 'spec.name', pattern = 'SchemaDirectiveWiringSpec(|\\.duplicate|\\.namedAndEveryElement|\\.replaced)')
    @Factory
    static class DirectiveWiringFactory {
        @Bean
        @Singleton
        @Order(30)
        SchemaDirective aThird() {
            return SchemaDirective.everyElement(['suffix'], new SuffixWiring('third'))
        }

        @Bean
        @Singleton
        @Order(20)
        SchemaDirective bSecond() {
            return SchemaDirective.everyElement(['suffix'], new SuffixWiring('second'))
        }

        @Bean
        @Singleton
        @Order(10)
        SchemaDirective cFirst() {
            return SchemaDirective.everyElement(['suffix'], new SuffixWiring('first'))
        }

        @Bean
        @Singleton
        SchemaDirective upper() {
            return SchemaDirective.named('upper', new UpperWiring())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_DUPLICATE)
    @Factory
    static class DuplicateFactory {
        @Bean
        @Singleton
        SchemaDirective anotherUpper() {
            return SchemaDirective.named('upper', new UpperWiring())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_NAMED_AND_EVERY_ELEMENT)
    @Factory
    static class NamedAndEveryElementFactory {
        @Bean
        @Singleton
        SchemaDirective bang() {
            return SchemaDirective.everyElement(['upper'], new BangWiring())
        }
    }

    static class BangWiring implements SchemaDirectiveWiring {
        @Override
        GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            return environment.element
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_REPLACED)
    @Singleton
    static class ReplaceUpper implements RuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.directive('upper', new UpperWiring())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_WRAPPED_BEAN)
    @Factory
    static class WrappedFactory {
        @Bean
        @Singleton
        UpperWiring upperWiring() {
            return new UpperWiring()
        }

        @Bean
        @Singleton
        SchemaDirective upper(UpperWiring upperWiring) {
            return SchemaDirective.named('upper', upperWiring)
        }

        @Bean
        @Singleton
        SchemaDirective suffix() {
            return SchemaDirective.everyElement(['suffix'], new SuffixWiring('only'))
        }
    }

    @Requires(property = 'spec.name', pattern = 'SchemaDirectiveWiringSpec\\.prototype(|Unwrapped)')
    @Prototype
    static class PrototypeUpperWiring extends UpperWiring {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_PROTOTYPE)
    @Factory
    static class PrototypeFactory {
        @Bean
        @Singleton
        SchemaDirective upper(PrototypeUpperWiring wiring) {
            return SchemaDirective.named('upper', wiring)
        }

        @Bean
        @Singleton
        SchemaDirective suffix() {
            return SchemaDirective.everyElement(['suffix'], new SuffixWiring('only'))
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_CUSTOMIZER_BEAN)
    @Singleton
    static class CustomizerUpperWiring extends UpperWiring {
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_CUSTOMIZER_BEAN)
    @Singleton
    static class RegisterUpper implements RuntimeWiringCustomizer {
        private final CustomizerUpperWiring wiring

        RegisterUpper(CustomizerUpperWiring wiring) {
            this.wiring = wiring
        }

        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.directive('upper', wiring)
            builder.directive('suffix', new SuffixWiring('only'))
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_UNWRAPPED_BEAN)
    @Factory
    static class UnwrappedFactory {
        @Bean
        @Singleton
        SchemaDirectiveWiring upperWiring() {
            return new UpperWiring()
        }
    }

    @Requires(property = 'spec.name', pattern = 'SchemaDirectiveWiringSpec(|\\.duplicate|\\.namedAndEveryElement|\\.replaced|\\.wrappedBean|\\.unwrappedBean|\\.prototype|\\.prototypeUnwrapped|\\.customizerBean)')
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }

        String plain() {
            return 'plain'
        }

        String decorated() {
            return 'decorated'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_VALIDATION)
    @Factory
    static class ValidationFactory {
        @Bean
        @Singleton
        ValidationSchemaWiring validationSchemaWiring() {
            return new ValidationSchemaWiring(ValidationRules.newValidationRules()
                    .onValidationErrorStrategy(OnValidationErrorStrategy.RETURN_NULL)
                    .build())
        }

        @Bean
        @Singleton
        SchemaDirective validation(ValidationSchemaWiring validationSchemaWiring) {
            return SchemaDirective.everyElement(
                    DirectiveConstraints.newDirectiveConstraints().build().constraints.keySet(),
                    validationSchemaWiring)
        }

        @Bean
        @Singleton
        TypeDefinitionRegistryCustomizer validationDirectives() {
            return { TypeDefinitionRegistry registry ->
                registry.merge(DirectiveConstraints.newDirectiveConstraints().build().directivesDeclaration)
            } as TypeDefinitionRegistryCustomizer
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_VALIDATION)
    @GraphQLRootResolver
    static class ValidatedQuery {
        String greet(String name) {
            return 'Hello, ' + name
        }
    }

}
