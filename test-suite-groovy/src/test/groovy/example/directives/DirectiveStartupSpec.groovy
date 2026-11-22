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
package example.directives

import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.manifest.BindingManifest
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class DirectiveStartupSpec extends Specification {

    static final String UNWIRED_DIRECTIVES = 'micronaut.graphql.tools.checks.unwired-directive'
    static final String STRICT = 'micronaut.graphql.tools.strict'
    static final String INERT_DIRECTIVES = 'micronaut.graphql.tools.allow.inert-directives'
    static final String SCHEMA_CUSTOMIZER_DIRECTIVES = 'micronaut.graphql.tools.allow.schema-customizer-directives'

    def 'a directive wired by a named and an every-element SchemaDirective fails the startup'() {
        when:
        Throwable e = failure([:], DirectiveFixtures.CONFLICT)

        then:
        e instanceof SchemaBindingException
        e.getCode() == BindingCode.DUPLICATE_DIRECTIVE_WIRING
        e.message.startsWith('[duplicate-directive-wiring] Directive @upper is wired by both a named and an every-element SchemaDirective bean')
    }

    def 'an applied directive without a wiring fails the startup by default'() {
        when:
        Throwable e = failure([:], DirectiveFixtures.UNWIRED)

        then:
        e instanceof SchemaBindingException
        e.getCode() == BindingCode.UNWIRED_DIRECTIVE
        e.message.startsWith('[unwired-directive] The schema applies directives that have no wiring: @legacy (Book)')
    }

    def 'unwired-directives ignore skips the check'() {
        when:
        ApplicationContext context = start([(UNWIRED_DIRECTIVES): 'ignore'], DirectiveFixtures.UNWIRED)

        then:
        context.getBean(GraphQLSchema) != null
        !context.getBean(BindingManifest).lines.contains('!unwired-directive legacy')

        cleanup:
        context?.close()
    }

    def 'unwired-directives warn starts and records the directive in the manifest'() {
        when:
        ApplicationContext context = start([(UNWIRED_DIRECTIVES): 'warn'], DirectiveFixtures.UNWIRED)

        then:
        context.getBean(GraphQLSchema) != null
        context.getBean(BindingManifest).lines.contains('!unwired-directive legacy')

        cleanup:
        context?.close()
    }

    def 'unwired-directives fail fails the startup even with strict off'() {
        expect:
        failure([(UNWIRED_DIRECTIVES): 'fail', (STRICT): false], DirectiveFixtures.UNWIRED).getCode() == BindingCode.UNWIRED_DIRECTIVE
    }

    def 'strict off alone starts and records the directive in the manifest'() {
        when:
        ApplicationContext context = start([(STRICT): false], DirectiveFixtures.UNWIRED)

        then:
        context.getBean(BindingManifest).lines.contains('!unwired-directive legacy')

        cleanup:
        context?.close()
    }

    def 'an inert directive starts without a wiring'() {
        when:
        ApplicationContext context = start([(INERT_DIRECTIVES): ['legacy']], DirectiveFixtures.UNWIRED)

        then:
        context.getBean(GraphQLSchema) != null
        !context.getBean(BindingManifest).lines.contains('!unwired-directive legacy')

        cleanup:
        context?.close()
    }

    def 'a schema-level directive that no schema customizer is listed for fails the startup'() {
        when:
        Throwable e = failure([(SCHEMA_CUSTOMIZER_DIRECTIVES): ['other']], DirectiveFixtures.SCHEMA_UNLISTED)

        then:
        e instanceof SchemaBindingException
        e.getCode() == BindingCode.UNWIRED_DIRECTIVE
        e.message.contains('@audit (schema definition)')
    }

    def 'every directive of a schema-level list starts the server'() {
        when:
        ApplicationContext context = start([(SCHEMA_CUSTOMIZER_DIRECTIVES): ['audit', 'other']], DirectiveFixtures.SCHEMA_UNLISTED)

        then:
        context.getBean(GraphQLSchema).getSchemaAppliedDirective('other') != null

        cleanup:
        context?.close()
    }

    private static Throwable failure(Map<String, Object> properties, String environment) {
        try {
            start(properties, environment).close()
        } catch (BeanInstantiationException e) {
            Throwable cause = e
            while (!(cause instanceof SchemaBindingException) && cause.cause != null) {
                cause = cause.cause
            }
            return cause
        }
        throw new AssertionError('the startup did not fail')
    }

    private static ApplicationContext start(Map<String, Object> properties, String environment) {
        ApplicationContext.run(EmbeddedServer, properties + ['micronaut.server.port': -1, 'micronaut.graphql.tools.manifest.record': true], environment).applicationContext
    }
}
