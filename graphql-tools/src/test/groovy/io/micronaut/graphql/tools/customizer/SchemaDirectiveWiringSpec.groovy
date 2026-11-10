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
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.RuntimeWiringCustomizer
import io.micronaut.graphql.tools.SchemaDirective
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.DuplicateDirectiveWiringException
import io.micronaut.graphql.tools.exceptions.RuntimeWiringOverrideException
import io.micronaut.graphql.tools.exceptions.UnwrappedDirectiveWiringException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

/**
 * The library applies a {@link SchemaDirectiveWiring} only through a {@link SchemaDirective} bean: a
 * {@link SchemaDirective#named} one is called only where the schema applies its directive, a
 * {@link SchemaDirective#everyElement} one for every schema element, lowest order first. A {@code SchemaDirectiveWiring}
 * bean that no {@code SchemaDirective} wraps fails the startup instead of running on every element. Both kinds work with
 * a hand-written wiring and with graphql-java-extended-validation.
 */
class SchemaDirectiveWiringSpec extends AbstractTest {

    static final String SPEC_NAME = "SchemaDirectiveWiringSpec"
    static final String SPEC_NAME_VALIDATION = "SchemaDirectiveWiringSpec.validation"
    static final String SPEC_NAME_DUPLICATE = "SchemaDirectiveWiringSpec.duplicate"
    static final String SPEC_NAME_REPLACED = "SchemaDirectiveWiringSpec.replaced"
    static final String SPEC_NAME_WRAPPED_BEAN = "SchemaDirectiveWiringSpec.wrappedBean"
    static final String SPEC_NAME_UNWRAPPED_BEAN = "SchemaDirectiveWiringSpec.unwrappedBean"

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
        e.cause instanceof UnwrappedDirectiveWiringException
        e.cause.beans == ["${UpperWiring.name} (Definition: ${UnwrappedFactory.name})".toString()]
        e.cause.message == "SchemaDirectiveWiring beans that no SchemaDirective bean wraps: ${e.cause.beans[0]}.\n" +
                "  The library applies a wiring only through a SchemaDirective: wrap each one in " +
                "SchemaDirective.named(name, wiring) (called where the directive is applied) or " +
                "SchemaDirective.everyElement(names, wiring) (called for every schema element), injecting the bean " +
                "into the SchemaDirective's factory method, or remove the bean."
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
        e.cause instanceof DuplicateDirectiveWiringException
        e.cause.message == "More than one SchemaDirective bean wires directive @upper: ${UpperWiring.name}, ${UpperWiring.name}. Keep one.".toString()
    }

    void "a RuntimeWiringCustomizer may not replace the wiring of a SchemaDirective bean"() {
        given:
        startContext(SCHEMA, SPEC_NAME_REPLACED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof RuntimeWiringOverrideException
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

    /**
     * Implements {@code @suffix}: appends "-{name}" to the field's value, so the order of the wirings shows.
     */
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

    @Requires(property = 'spec.name', pattern = 'SchemaDirectiveWiringSpec(|\\.duplicate|\\.replaced)')
    @Factory
    static class DirectiveWiringFactory {
        // declared, and named alphabetically, in the reverse of their order
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

    @Requires(property = 'spec.name', value = SPEC_NAME_UNWRAPPED_BEAN)
    @Factory
    static class UnwrappedFactory {
        @Bean
        @Singleton
        SchemaDirectiveWiring upperWiring() {
            return new UpperWiring()
        }
    }

    @Requires(property = 'spec.name', pattern = 'SchemaDirectiveWiringSpec(|\\.duplicate|\\.replaced|\\.wrappedBean|\\.unwrappedBean)')
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

        // the wiring is a bean too: wrapping it is what applies it, once
        @Bean
        @Singleton
        SchemaDirective validation(ValidationSchemaWiring validationSchemaWiring) {
            return SchemaDirective.everyElement(
                    DirectiveConstraints.newDirectiveConstraints().build().constraints.keySet(),
                    validationSchemaWiring)
        }

        // the extended-validation directives (@Size, @Pattern, ...) are declared by the library, not in the SDL
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
