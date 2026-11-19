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
package io.micronaut.graphql.tools

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.binding.internal.finding.DefaultSeverities
import io.micronaut.graphql.tools.binding.internal.finding.Severity
import org.slf4j.LoggerFactory

import static io.micronaut.graphql.tools.StrictModeSpec.ItemResolver
import static io.micronaut.graphql.tools.StrictModeSpec.Query
import static io.micronaut.graphql.tools.StrictModeSpec.Color
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.internal.GraphQLRuntimeWiringGenerator

/**
 * Per-check severity (#86): the defaults, the strict shorthand, explicit entries, the validation of the settings and
 * the allow lists. The four checks of {@link StrictModeSpec}'s schema stand for the strict checks; the corpus has one
 * case per check and severity.
 */
class CheckSeveritySpec extends AbstractTest {

    static final String SPEC_NAME = StrictModeSpec.SPEC_NAME
    static final String PREFIX = 'micronaut.graphql.tools.'

    static final Map<String, String> LINES = [
            'unbound-resolver-method': "!unbound-resolver-method ${Query.name}#helper(java.lang.String)",
            'unbound-enum-constant'  : "!unbound-enum-constant ${Color.name}#BLUE",
            'unused-dictionary-entry': "!unused-dictionary-entry Ghost = ${StrictModeSpec.Item.name}",
    ]
    static final String LEGACY = "!unbound-resolver-method ${ItemResolver.name}#legacyName(${StrictModeSpec.Item.name})"

    ListAppender<ILoggingEvent> appender

    void setup() {
        appender = new ListAppender<>()
        appender.start()
        logger().addAppender(appender)
    }

    void cleanup() {
        logger().detachAppender(appender)
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator)
    }

    private Throwable startFailure(Map config) {
        startContext(StrictModeSpec.SCHEMA, SPEC_NAME, config)
        try {
            getGraphQLBean()
            return null
        } catch (BeanInstantiationException e) {
            return e.cause
        }
    }

    void "the default table has every check at fail, and nothing else"() {
        expect:
        DefaultSeverities.TABLE.keySet() == BindingCode.values().findAll { it.check() }.toSet()
        DefaultSeverities.TABLE.values().every { it == Severity.FAIL }
        BindingCode.values().findAll { it.check() }.every {
            DefaultSeverities.of(it, true) == Severity.FAIL && DefaultSeverities.of(it, false) == Severity.WARN
        }
    }

    void "with no settings every check fails"() {
        when:
        def failure = startFailure([:])

        then:
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.STRICT_FINDINGS
        failure.codes.toSet() == [BindingCode.UNBOUND_RESOLVER_METHOD, BindingCode.UNBOUND_ENUM_CONSTANT,
                                  BindingCode.UNUSED_DICTIONARY_ENTRY] as Set
    }

    void "with strict false every check warns: the startup goes on, each warning has its code, the manifest lists the findings"() {
        when:
        def failure = startFailure([(PREFIX + 'strict'): false])

        then:
        failure == null
        appender.list.findAll { it.level == Level.WARN }*.formattedMessage.collect { it.substring(0, it.indexOf(']') + 1) }
                .sort() == ['[unbound-enum-constant]', '[unbound-resolver-method]', '[unused-dictionary-entry]']
        applicationContext.getBean(BindingManifest).diagnostics.toSet() == (LINES.values() + [LEGACY]).toSet()
    }

    void "an explicit fail wins over strict false"() {
        when:
        def failure = startFailure([(PREFIX + 'strict'): false, (PREFIX + 'checks.unbound-enum-constant'): 'fail'])

        then: 'only that check fails; the others are warnings'
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.STRICT_FINDINGS
        failure.codes == [BindingCode.UNBOUND_ENUM_CONSTANT]
        appender.list.findAll { it.level == Level.WARN }.size() == 2
    }

    void "an explicit warn or ignore wins over a strict default for that check only"() {
        when:
        def failure = startFailure([
                (PREFIX + 'checks.unbound-enum-constant')  : 'ignore',
                (PREFIX + 'checks.unused-dictionary-entry'): 'warn'])

        then: 'the other check still fails'
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.STRICT_FINDINGS
        failure.codes.toSet() == [BindingCode.UNBOUND_RESOLVER_METHOD] as Set
    }

    void "each severity of each check: fail fails, warn logs and lists, ignore does nothing and lists nothing (#code, #severity)"() {
        when:
        def settings = [(PREFIX + 'checks.' + code): severity] + others(code)
        def failure = startFailure(settings)

        then:
        if (severity == 'fail') {
            assert failure.code() == BindingCode.STRICT_FINDINGS
            assert failure.codes.contains(BindingCode.fromCode(code))
        } else {
            assert failure == null
            def lines = applicationContext.getBean(BindingManifest).diagnostics.findAll { it.startsWith('!' + code + ' ') }
            def warned = appender.list.findAll {
                it.level == Level.WARN && it.formattedMessage.startsWith("[${code}] ")
            }
            if (severity == 'warn') {
                assert !lines.isEmpty()
                assert warned.size() == 1
                assert warned[0].formattedMessage.startsWith("[${code}] micronaut.graphql.tools.checks.${code} is warn, so the startup goes on:")
            } else {
                assert lines.isEmpty()
                assert warned.isEmpty()
            }
        }

        where:
        [code, severity] << ['unbound-resolver-method', 'unbound-enum-constant', 'unused-dictionary-entry'].collectMany { c ->
            ['fail', 'warn', 'ignore'].collect { [c, it] }
        }
    }

    private static Map others(String code) {
        ['unbound-resolver-method', 'unbound-enum-constant', 'unused-dictionary-entry']
                .findAll { it != code }.collectEntries { [(PREFIX + 'checks.' + it): 'ignore'] }
    }

    void "an unknown check, a failure code and an invalid severity fail the startup with all problems named"() {
        when:
        def failure = startFailure([
                (PREFIX + 'checks.unbound-method'): 'warn',
                (PREFIX + 'checks.field-not-bound'): 'warn',
                (PREFIX + 'checks.unbound-enum-constant'): 'sometimes'])

        then:
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.INVALID_CHECK_SETTING
        failure.problems.size() == 3
        failure.message.startsWith('[invalid-check-setting] The checks setting is invalid:')
        failure.message.contains('micronaut.graphql.tools.checks.unbound-method: not a code of the registry')
        failure.message.contains('micronaut.graphql.tools.checks.field-not-bound: a failure code, not a check')
        failure.message.contains('"sometimes" is not a severity; use fail, warn, ignore.')
    }

    void "each removed key fails the startup, names its replacement and has its code (#key)"() {
        when:
        def failure = startFailure([(PREFIX + key): value])

        then:
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.REMOVED_CONFIGURATION_KEY
        failure.message.startsWith('[removed-configuration-key] ')
        failure.message.contains("${PREFIX}${key} was removed; use ${PREFIX}${replacement}")

        where:
        key                              | value        || replacement
        'unreachable-types'              | 'ignore'     || 'checks.unreachable-type'
        'allow-unreachable'              | ['Orphan']   || 'allow.unreachable'
        'input.unmapped-properties'      | 'ignore'     || 'checks.unmapped-input-property'
        'inert-directives'               | ['auth']     || 'allow.inert-directives'
        'schema-customizer-directives'   | ['audit']    || 'allow.schema-customizer-directives'
        'unwired-directives'             | 'ignore'     || 'checks.unwired-directive'
    }

    void "the removed input.detect-omission fails the startup and names ArgumentValue"() {
        when:
        def failure = startFailure([(PREFIX + 'input.detect-omission'): true])

        then:
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.REMOVED_CONFIGURATION_KEY
        failure.message.contains("${PREFIX}input.detect-omission was removed; declare ArgumentValue")
    }

    void "all removed keys are named in one error"() {
        when:
        def failure = startFailure([(PREFIX + 'unreachable-types'): 'fail', (PREFIX + 'allow-unreachable'): ['A']])

        then:
        failure.problems.size() == 2
    }

    void "a manifest line pasted into allow.findings silences exactly that finding"() {
        when:
        def failure = startFailure([(PREFIX + 'allow.findings'): [LINES['unbound-resolver-method']]])

        then: 'the other findings still fail, this one is neither reported nor listed'
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.STRICT_FINDINGS
        failure.codes.count(BindingCode.UNBOUND_RESOLVER_METHOD) == 1
        failure.problems.every { !it.contains('#helper(') }
        failure.problems.any { it.contains('#legacyName(') }
    }

    void "the lines of strict false, pasted into allow.findings, accept every finding with no warning and no manifest line"() {
        given: 'the lines that the manifest of strict false has'
        startContext(StrictModeSpec.SCHEMA, SPEC_NAME, [(PREFIX + 'strict'): false])
        getGraphQLBean()
        def lines = applicationContext.getBean(BindingManifest).diagnostics.toList()
        applicationContext.close()
        appender.list.clear()

        when:
        def failure = startFailure([(PREFIX + 'allow.findings'): lines])

        then:
        lines.size() == 4
        failure == null
        applicationContext.getBean(BindingManifest).diagnostics.isEmpty()
        appender.list.findAll { it.level == Level.WARN }.isEmpty()
    }

    void "an allow.findings entry that matches no finding is stale and fails the startup"() {
        when:
        def failure = startFailure([(PREFIX + 'allow.findings'): LINES.values().toList() + [LEGACY, '!unbound-resolver-method example.Gone#method()']])

        then:
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.INVALID_ALLOW_FINDINGS
        failure.problems == ['micronaut.graphql.tools.allow.findings: "!unbound-resolver-method example.Gone#method()" matches no finding.']
    }

    void "an allow.findings entry for a check at ignore has no effect and fails the startup"() {
        when:
        def failure = startFailure([(PREFIX + 'checks.unbound-enum-constant'): 'ignore',
                                    (PREFIX + 'allow.findings')                 : [LINES['unbound-enum-constant']]])

        then:
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.INVALID_ALLOW_FINDINGS
        failure.problems[0].contains('has no effect, micronaut.graphql.tools.checks.unbound-enum-constant is ignore')
        failure.message.startsWith('[invalid-allow-findings] ')
    }

    void "an allow.findings entry that is not the line of a check fails the startup (#entry)"() {
        when:
        def failure = startFailure([(PREFIX + 'allow.findings'): [entry]])

        then:
        failure instanceof SchemaBindingException
        failure.code() == BindingCode.INVALID_ALLOW_FINDINGS
        failure.message.contains("\"${entry}\" is not a line of the binding manifest that a check writes")

        where:
        entry << ['unbound-resolver-method x', '!no-such-check x', '!field-not-bound Query.x', '!precedence-ambiguity Item.name', '!unbound-resolver-method', '~ignored x']
    }

    void "check names are accepted in the forms Micronaut normalizes"() {
        when:
        def failure = startFailure([(PREFIX + 'checks'): ['unboundEnumConstant': 'ignore', 'unbound-resolver-method': 'ignore',
                                                         'unused_dictionary_entry': 'ignore']])

        then:
        failure == null
    }
}
