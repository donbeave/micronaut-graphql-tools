package io.micronaut.graphql.tools.customizer

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaDirectiveWiring
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.context.BeanRegistration
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.RuntimeWiringCustomizer
import io.micronaut.graphql.tools.SchemaDirective
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.UnwiredDirectivesException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

class UnwiredDirectivesSpec extends AbstractTest {

    static final String SPEC_NAME = "UnwiredDirectivesSpec"
    static final String SPEC_NAME_WIRED = "UnwiredDirectivesSpec.wired"
    static final String SPEC_NAME_CUSTOMIZER = "UnwiredDirectivesSpec.customizer"
    static final String SPEC_NAME_KINDS = "UnwiredDirectivesSpec.kinds"
    static final String SPEC_NAME_PLACES = "UnwiredDirectivesSpec.places"

    @Language("GraphQL")
    static final String PLACES_SCHEMA = """
schema @audit {
  query: Query
}

directive @audit on SCHEMA
directive @doc on ARGUMENT_DEFINITION
directive @meta(x: Int @doc) on FIELD_DEFINITION
directive @fedKey on FIELD_DEFINITION
directive @fedLink on FIELD_DEFINITION

type Query {
  r: String @meta(x: 1) @fedKey @fedLink
}
"""

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

directive @auth on OBJECT | FIELD_DEFINITION | INPUT_FIELD_DEFINITION | ENUM_VALUE
directive @cached(ttl: Int) on ARGUMENT_DEFINITION
directive @unused on FIELD_DEFINITION
directive @upper on FIELD_DEFINITION

type Query {
  me: User @auth
  users: [User] @auth
  items(limit: Int @cached(ttl: 10)): [String]
  old: String @deprecated(reason: "use me")
  shout: String @upper
  amount: Long
}

type User @auth {
  email: String @auth
}

enum Role {
  ADMIN @auth
  USER @deprecated
}

input Filter {
  name: String @auth
}

input Pick @oneOf {
  a: String
  b: String
}

scalar Long @specifiedBy(url: "https://example.com/long")
"""

    static final Map ALLOW = ['micronaut.graphql.tools.allow-unreachable': ['Filter', 'Pick', 'Role']]

    static final String UNWIRED_MESSAGE = """The schema applies directives that have no wiring: @auth (Filter.name, Query.me, Query.users, Role.ADMIN, User and 1 more), @cached (Query.items(limit:)).
  Wire each one with a SchemaDirective bean that names it (SchemaDirective.named, or SchemaDirective.everyElement for a wiring that sees every element), or list it in micronaut.graphql.tools.inert-directives if it has no effect at runtime."""

    ListAppender<ILoggingEvent> appender

    void setup() {
        appender = new ListAppender<>()
        appender.start()
        checkLogger().addAppender(appender)
    }

    void cleanup() {
        checkLogger().detachAppender(appender)
    }

    void "applied directives without a wiring fail the startup, all of them, with where they are applied"() {
        given: 'a named wiring for @upper only'
        startContext(SCHEMA, SPEC_NAME, ALLOW)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnwiredDirectivesException
        e.cause.unwiredDirectives == ['auth', 'cached']
        e.cause.invalidInertDirectives == []
        e.cause.message == "[unwired-directive] " + UNWIRED_MESSAGE
    }

    void "directives on every kind of schema element are found, with their coordinates"() {
        given: 'interfaces bind with a later change: unreachable types are ignored here'
        @Language("GraphQL")
        String schema = """
schema @onSchema {
  query: Query
}

extend schema @onSchemaExt

directive @onSchema on SCHEMA
directive @onSchemaExt on SCHEMA
directive @onIface on INTERFACE
directive @onIfaceField on FIELD_DEFINITION
directive @onIfaceArg on ARGUMENT_DEFINITION
directive @onUnion on UNION
directive @onExt on OBJECT
directive @onExtField on FIELD_DEFINITION
directive @onInputExt on INPUT_FIELD_DEFINITION
directive @onScalar on SCALAR
directive @onDirArg on ARGUMENT_DEFINITION
directive @meta(x: Int @onDirArg) on FIELD_DEFINITION
directive @rep repeatable on FIELD_DEFINITION

type Query {
  r: String @rep @rep
}

extend type Query @onExt {
  e: String @onExtField
}

interface Node @onIface {
  id: ID @onIfaceField
  f(a: Int @onIfaceArg): String
}

union U @onUnion = Query

input In {
  a: String
}

extend input In {
  b: String @onInputExt
}

scalar Sc @onScalar
"""
        startContext(schema, SPEC_NAME_KINDS, ['micronaut.graphql.tools.unreachable-types': 'ignore'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnwiredDirectivesException
        e.cause.unwiredDirectives == ['onDirArg', 'onExt', 'onExtField', 'onIface', 'onIfaceArg', 'onIfaceField',
                                       'onInputExt', 'onScalar', 'onSchema', 'onSchemaExt', 'onUnion', 'rep']
        e.cause.message.startsWith('[unwired-directive] The schema applies directives that have no wiring: ' +
                '@onDirArg (@meta(x:)), @onExt (Query), @onExtField (Query.e), @onIface (Node), ' +
                '@onIfaceArg (Node.f(a:)), @onIfaceField (Node.id), @onInputExt (In.b), @onScalar (Sc), ' +
                '@onSchema (schema), @onSchemaExt (schema), @onUnion (U), @rep (Query.r).\n')
    }

    void "inert-directives lets the listed directives through and logs them"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ALLOW + ['micronaut.graphql.tools.inert-directives': ['cached', 'auth']])

        when:
        def result = executeQuery('{ me { email } shout amount }')

        then:
        result.errors.isEmpty()
        result.data == [me: [email: 'me@example.com'], shout: 'SHOUT', amount: 1L]
        applicationContext.getBean(BindingManifest).lines.every { !it.startsWith('!unwired-directive ') }
        appender.list.any {
            it.level == Level.INFO && it.formattedMessage == 'Directives without a wiring, declared inert by ' +
                    'micronaut.graphql.tools.inert-directives: @auth, @cached'
        }
    }

    void "an inert-directives entry that is not declared or that has a wiring fails the startup, with the rest"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ALLOW + ['micronaut.graphql.tools.inert-directives': ['auth', 'missing', 'upper', 'deprecated']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnwiredDirectivesException
        e.cause.unwiredDirectives == ['cached']
        e.cause.invalidInertDirectives == ['deprecated', 'missing', 'upper']
        e.cause.message == """[unwired-directive] The schema applies directives that have no wiring: @cached (Query.items(limit:)).
  Wire each one with a SchemaDirective bean that names it (SchemaDirective.named, or SchemaDirective.everyElement for a wiring that sees every element), or list it in micronaut.graphql.tools.inert-directives if it has no effect at runtime.
[invalid-inert-directives] micronaut.graphql.tools.inert-directives lists directives that are built in, not declared in the schema, or that have a wiring: @deprecated (built into graphql-java), @missing (not declared in the schema), @upper (has a wiring).
  Remove them from the list."""
    }

    void "with strict false, unwired directives are a WARN that lists them, and !unwired-directive lines"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ALLOW + ['micronaut.graphql.tools.strict': false])

        when:
        def result = executeQuery('{ me { email } shout }')

        then: 'the same directives and coordinates as the error of strict mode'
        result.errors.isEmpty()
        result.data == [me: [email: 'me@example.com'], shout: 'SHOUT']
        appender.list.findAll { it.level == Level.WARN }*.formattedMessage == [
                'micronaut.graphql.tools.strict is false, so the startup goes on: ' + UNWIRED_MESSAGE]

        and: 'one line per unwired directive; built-in and wired ones have none'
        applicationContext.getBean(BindingManifest).lines.findAll { it.startsWith('!unwired-directive ') } == [
                '!unwired-directive auth', '!unwired-directive cached']
    }

    void "with strict false, invalid inert-directives entries still fail the startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ALLOW + [
                'micronaut.graphql.tools.strict'          : false,
                'micronaut.graphql.tools.inert-directives': ['cached', 'missing', 'upper']])

        when:
        getGraphQLBean()

        then: 'only the invalid entries fail; @auth is a WARN'
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnwiredDirectivesException
        e.cause.unwiredDirectives == []
        e.cause.invalidInertDirectives == ['missing', 'upper']
        appender.list.findAll { it.level == Level.WARN }*.formattedMessage == [
                'micronaut.graphql.tools.strict is false, so the startup goes on: The schema applies directives that ' +
                        'have no wiring: @auth (Filter.name, Query.me, Query.users, Role.ADMIN, User and 1 more).\n' +
                        UNWIRED_MESSAGE.substring(UNWIRED_MESSAGE.indexOf('  Wire each'))]
    }

    void "a resolver-directives entry counts as wired, so it cannot be listed as inert"() {
        given: '@unused is declared and never applied, which resolver-directives accepts'
        startContext(SCHEMA, SPEC_NAME, ALLOW + [
                'micronaut.graphql.tools.resolver-directives': ['unused'],
                'micronaut.graphql.tools.inert-directives'   : ['auth', 'cached', 'unused']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnwiredDirectivesException
        e.cause.unwiredDirectives == []
        e.cause.invalidInertDirectives == ['unused']
        e.cause.message.contains('@unused (listed in micronaut.graphql.tools.resolver-directives)')
    }

    void "SchemaDirective.everyElement names the directives a wiring for every element implements"() {
        given:
        startContext(SCHEMA, SPEC_NAME_WIRED, ALLOW)

        when:
        def result = executeQuery('{ me { email } shout }')

        then:
        result.errors.isEmpty()
        result.data == [me: [email: 'me@example.com'], shout: 'SHOUT']
        appender.list.any {
            it.level == Level.INFO && it.formattedMessage == 'SchemaDirective beans wire directives that the ' +
                    'schema does not declare; they are not used: @elsewhere'
        }
    }

    void "a directive wiring that a RuntimeWiringCustomizer registers counts"() {
        given:
        startContext(SCHEMA, SPEC_NAME_CUSTOMIZER, ALLOW + ['micronaut.graphql.tools.inert-directives': ['cached']])

        when:
        def result = executeQuery('{ me { email } shout }')

        then:
        result.errors.isEmpty()
        result.data == [me: [email: 'me@example.com'], shout: 'SHOUT']
    }

    void "a wiring does not count for the schema definition or a directive argument"() {
        given: 'named wirings for @audit (on schema), @doc (on a directive argument) and @meta'
        startContext(PLACES_SCHEMA, SPEC_NAME_PLACES, ['micronaut.graphql.tools.inert-directives': ['fed*']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnwiredDirectivesException
        e.cause.unwiredDirectives == ['audit', 'doc']
        e.cause.message == """[unwired-directive] The schema applies directives that have no wiring: @audit (schema), @doc (@meta(x:)).
  Wire each one with a SchemaDirective bean that names it (SchemaDirective.named, or SchemaDirective.everyElement for a wiring that sees every element), or list it in micronaut.graphql.tools.inert-directives if it has no effect at runtime.
  graphql-java calls no wiring for the schema definition (schema) or for directive arguments (@name(arg:)), so a directive applied there must be listed in inert-directives, or in micronaut.graphql.tools.schema-customizer-directives if a GraphQLSchemaCustomizer handles it."""
    }

    void "schema-customizer-directives and inert-directives accept the schema-level and argument-level ones"() {
        given:
        startContext(PLACES_SCHEMA, SPEC_NAME_PLACES, [
                'micronaut.graphql.tools.schema-customizer-directives': ['audit'],
                'micronaut.graphql.tools.inert-directives'            : ['fed*']])

        when:
        def e = null
        try {
            getGraphQLBean()
        } catch (BeanInstantiationException ex) {
            e = ex.cause
        }

        then: '@doc is still unwired: only the schema-level directive was listed'
        e instanceof UnwiredDirectivesException
        e.unwiredDirectives == ['doc']
    }

    void "an inert-directives pattern that matches no directive without a wiring, and invalid schema-customizer-directives entries, fail the startup"() {
        given:
        startContext(PLACES_SCHEMA, SPEC_NAME_PLACES, [
                'micronaut.graphql.tools.schema-customizer-directives': ['audit', 'doc', 'deprecated', 'missing'],
                'micronaut.graphql.tools.inert-directives'            : ['fed*', 'gone*', '*Key']])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnwiredDirectivesException
        e.cause.unwiredDirectives == []
        e.cause.invalidInertDirectives == ['gone*']
        e.cause.invalidSchemaCustomizerDirectives == ['deprecated', 'missing']
        e.cause.message.contains('@gone* (matches no declared directive that lacks a wiring)')
        e.cause.message.contains('@deprecated (built into graphql-java), @missing (not declared in the schema)')
    }

    void "unwired-directives: ignore skips the check, warn logs it, fail fails also with strict false"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ALLOW + ['micronaut.graphql.tools.unwired-directives': mode] + extra)

        when:
        Throwable failure = null
        try {
            getGraphQLBean()
        } catch (BeanInstantiationException ex) {
            failure = ex.cause
        }

        then:
        (failure instanceof UnwiredDirectivesException) == fails
        appender.list.findAll { it.level == Level.WARN }.size() == warnings

        where:
        mode     | extra                                    || fails | warnings
        'ignore' | [:]                                      || false | 0
        'warn'   | [:]                                      || false | 1
        'fail'   | ['micronaut.graphql.tools.strict': false] || true  | 0
    }

    void "the wiring beans are injected into the schema factory method, so the DI graph shows them"() {
        given:
        startContext(SCHEMA, SPEC_NAME, ALLOW + ['micronaut.graphql.tools.inert-directives': ['auth', 'cached']])

        when:
        def arguments = applicationContext.getBeanDefinition(GraphQLSchema).constructor.arguments.toList()
        def schemaDirectives = arguments.find { it.name == 'schemaDirectives' }
        def directiveWirings = arguments.find { it.name == 'directiveWirings' }

        then:
        schemaDirectives.type == List
        schemaDirectives.typeParameters[0].type == BeanRegistration
        schemaDirectives.typeParameters[0].typeParameters[0].type == SchemaDirective
        directiveWirings.typeParameters[0].typeParameters[0].type == SchemaDirectiveWiring
    }

    private static Logger checkLogger() {
        return (Logger) LoggerFactory.getLogger('io.micronaut.graphql.tools.DirectiveWiringCheck')
    }

    static class NoOpWiring implements SchemaDirectiveWiring {
    }

    @Requires(property = 'spec.name', pattern = 'UnwiredDirectivesSpec.*')
    @Factory
    static class DirectiveFactory {
        @Bean
        @Singleton
        SchemaDirective upper() {
            return SchemaDirective.named('upper', new SchemaDirectiveWiringSpec.UpperWiring())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_WIRED)
    @Factory
    static class WiredFactory {
        @Bean
        @Singleton
        SchemaDirective auth() {
            return SchemaDirective.named('auth', new NoOpWiring())
        }

        @Bean
        @Singleton
        SchemaDirective cachedAndElsewhere() {
            return SchemaDirective.everyElement(['cached', 'elsewhere'], new NoOpWiring())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_CUSTOMIZER)
    @Singleton
    static class RegistersAuth implements RuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.directive('auth', new NoOpWiring())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_PLACES)
    @Factory
    static class PlacesFactory {
        @Bean
        @Singleton
        SchemaDirective audit() {
            return SchemaDirective.named('audit', new NoOpWiring())
        }

        @Bean
        @Singleton
        SchemaDirective doc() {
            return SchemaDirective.named('doc', new NoOpWiring())
        }

        @Bean
        @Singleton
        SchemaDirective meta() {
            return SchemaDirective.named('meta', new NoOpWiring())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_PLACES)
    @GraphQLRootResolver
    static class PlacesQuery {
        String r() {
            return 'r'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_KINDS)
    @GraphQLRootResolver
    static class KindsQuery {
        String r() {
            return 'r'
        }

        String e() {
            return 'e'
        }
    }

    @Requires(property = 'spec.name', pattern = 'UnwiredDirectivesSpec(|\\.wired|\\.customizer)')
    @GraphQLRootResolver
    static class Query {
        User me() {
            return new User()
        }

        List<User> users() {
            return [new User()]
        }

        List<String> items(Integer limit) {
            return ['item']
        }

        String old() {
            return 'old'
        }

        String shout() {
            return 'shout'
        }

        Long amount() {
            return 1L
        }
    }

    @GraphQLType
    static class User {
        String email = 'me@example.com'
    }

}
