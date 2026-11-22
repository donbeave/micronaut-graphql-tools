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
package io.micronaut.graphql.tools.security

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.execution.instrumentation.Instrumentation
import graphql.language.OperationDefinition
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.DefaultRolesFinder
import io.micronaut.security.token.RolesFinder
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/**
 * The options of the module: the enabled switch reaches the root fields hook, a replaced {@code RolesFinder} decides on the roles
 * of a secured field, and an expression in {@code @Secured} is rejected at startup.
 */
class SecurityOptionsSpec extends Specification {

    static final AtomicInteger HOOK_CREATED = new AtomicInteger()
    static final AtomicInteger HOOK_CALLED = new AtomicInteger()

    @Language('GraphQL')
    static final String SCHEMA = 'schema { query: Query } type Query { open: String admin: String }'

    @Language('GraphQL')
    static final String EXPRESSION_SCHEMA = 'schema { query: Query } type Query { gated: String }'

    void setup() {
        HOOK_CREATED.set(0)
        HOOK_CALLED.set(0)
    }

    private static ApplicationContext start(String spec, String schema, Map<String, Object> properties = [:]) {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry()
        registry.merge(new SchemaParser().parse(schema))
        ApplicationContext.builder(['spec.name': spec] + properties, 'test').singletons(registry).start()
    }

    private static ExecutionResult run(ApplicationContext context, String query, Authentication authentication = null) {
        ExecutionInput.Builder input = ExecutionInput.newExecutionInput().query(query)
        if (authentication != null) {
            input.graphQLContext([(GraphQLSecurity.AUTHENTICATION_KEY): authentication])
        }
        context.getBean(GraphQL).execute(input.build())
    }

    private static boolean hasRootFieldsInstrumentation(ApplicationContext context) {
        context.getBeansOfType(Instrumentation).any { it.class.simpleName == 'RootFieldsInstrumentation' }
    }

    void "the root fields hook is created and called while the module is enabled"() {
        given:
        def context = start('SecurityOptionsSpec.hook', SCHEMA)

        when:
        def result = run(context, '{ open }')

        then:
        result.errors.isEmpty()
        hasRootFieldsInstrumentation(context)
        HOOK_CREATED.get() == 1
        HOOK_CALLED.get() == 1

        cleanup:
        context?.close()
    }

    void "with the module disabled the hook bean is not created and no root field check runs"() {
        given:
        def context = start('SecurityOptionsSpec.hook', SCHEMA, ['micronaut.graphql.tools.security.enabled': false])

        when:
        def result = run(context, '{ open }')

        then:
        result.errors.isEmpty()
        result.getData() == [open: 'open']
        !hasRootFieldsInstrumentation(context)
        HOOK_CREATED.get() == 0
        HOOK_CALLED.get() == 0

        cleanup:
        context?.close()
    }

    void "a RolesFinder that the application replaces decides on the roles of a secured field"() {
        given:
        def context = start('SecurityOptionsSpec.finder', SCHEMA)

        expect: 'the replaced finder maps TEAM_OPS to ROLE_ADMIN and does not know ROLE_ADMIN itself'
        context.getBean(RolesFinder) instanceof TeamRolesFinder
        run(context, '{ admin }', Authentication.build('ops', ['TEAM_OPS'])).getData() == [admin: 'admin']
        run(context, '{ admin }', Authentication.build('root', ['ROLE_ADMIN'])).errors*.extensions.code == ['FORBIDDEN']

        cleanup:
        context?.close()
    }

    void "the default RolesFinder keeps the plain role lookup"() {
        given:
        def context = start('SecurityOptionsSpec.plain', SCHEMA)

        expect:
        context.getBean(RolesFinder).class == DefaultRolesFinder
        run(context, '{ admin }', Authentication.build('root', ['ROLE_ADMIN'])).getData() == [admin: 'admin']
        run(context, '{ admin }', Authentication.build('ops', ['TEAM_OPS'])).errors*.extensions.code == ['FORBIDDEN']

        cleanup:
        context?.close()
    }

    void "an application with Micronaut Security switched off still starts and keeps the plain role lookup"() {
        given:
        def context = start('SecurityOptionsSpec.plain', SCHEMA, ['micronaut.security.enabled': false])

        expect:
        !context.containsBean(RolesFinder)
        run(context, '{ admin }', Authentication.build('root', ['ROLE_ADMIN'])).getData() == [admin: 'admin']
        run(context, '{ admin }', Authentication.build('ops', ['TEAM_OPS'])).errors*.extensions.code == ['FORBIDDEN']

        cleanup:
        context?.close()
    }

    void "an expression in @Secured is rejected at startup with a message that names the field"() {
        when:
        start('SecurityOptionsSpec.expression', EXPRESSION_SCHEMA)

        then:
        def e = thrown(Exception)
        def messages = causeMessages(e)
        messages.any { it.contains('SecurityOptionsSpec\$ExpressionQueries gated()') && it.contains('Query.gated') && it.contains('expression value') && it.contains('does not evaluate expressions') }
    }

    void "with the module disabled an expression in @Secured is not looked at"() {
        when:
        def context = start('SecurityOptionsSpec.expression', EXPRESSION_SCHEMA, ['micronaut.graphql.tools.security.enabled': false])

        then:
        run(context, '{ gated }').getData() == [gated: 'gated']

        cleanup:
        context?.close()
    }

    private static List<String> causeMessages(Throwable throwable) {
        List<String> messages = []
        for (Throwable t = throwable; t != null; t = t.cause) {
            messages << String.valueOf(t.message)
        }
        messages
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'SecurityOptionsSpec.hook')
    static class CountingHook implements RootFieldsHook {

        CountingHook() {
            HOOK_CREATED.incrementAndGet()
        }

        @Override
        void onRootFields(OperationDefinition.Operation operation, List<String> rootFields, GraphQLContext context) {
            HOOK_CALLED.incrementAndGet()
        }
    }

    @Singleton
    @Replaces(DefaultRolesFinder)
    @Requires(property = 'spec.name', value = 'SecurityOptionsSpec.finder')
    static class TeamRolesFinder implements RolesFinder {

        @Override
        List<String> resolveRoles(Map<String, Object> attributes) {
            []
        }

        @Override
        boolean hasAnyRequiredRoles(List<String> requiredRoles, Collection<String> grantedRoles) {
            List<String> mapped = grantedRoles.findAll { it != 'ROLE_ADMIN' }.collect { it == 'TEAM_OPS' ? 'ROLE_ADMIN' : it }
            mapped.any { requiredRoles.contains(it) }
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = 'spec.name', pattern = 'SecurityOptionsSpec\\.(hook|finder|plain)')
    static class Queries {

        String open() {
            'open'
        }

        @Secured('ROLE_ADMIN')
        String admin() {
            'admin'
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = 'spec.name', value = 'SecurityOptionsSpec.expression')
    static class ExpressionQueries {

        @Secured('''#{user.name == 'x'}''')
        String gated() {
            'gated'
        }
    }
}
