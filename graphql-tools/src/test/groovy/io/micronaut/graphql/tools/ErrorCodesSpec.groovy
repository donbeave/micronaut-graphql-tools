package io.micronaut.graphql.tools

import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.AbstractMappingException
import io.micronaut.graphql.tools.exceptions.BindingFailuresException
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.exceptions.StrictModeException
import io.micronaut.graphql.tools.exceptions.UnmappedInputPropertiesException
import io.micronaut.graphql.tools.exceptions.UnreachableTypesException
import io.micronaut.graphql.tools.exceptions.UnwiredDirectivesException
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths

class ErrorCodesSpec extends Specification {

    void "every exception of the library is a SchemaBindingException, so it has a code"() {
        given:
        def location = Paths.get(SchemaBindingException.protectionDomain.codeSource.location.toURI())
        def dir = location.resolve('io/micronaut/graphql/tools/exceptions')

        when:
        def classes = Files.list(dir).withCloseable { stream ->
            stream.map { it.fileName.toString() }
                    .filter { it.endsWith('.class') && !it.contains('$') }
                    .map { Class.forName('io.micronaut.graphql.tools.exceptions.' + it.substring(0, it.length() - 6)) }
                    .toList()
        }

        then:
        classes.size() > 30
        classes.findAll { !SchemaBindingException.isAssignableFrom(it) }.isEmpty()
        SchemaBindingException.isAssignableFrom(AbstractMappingException)
    }

    void "the message starts with the code in square brackets, and the exception exposes the same code"() {
        given:
        def failure = new UnreachableTypesException([Orphan: 'type'])

        expect:
        failure.code() == BindingCode.UNREACHABLE_TYPE
        failure.message.startsWith('[unreachable-type] ')
    }

    void "unreachable enum, input and scalar types have the value-type code"() {
        expect:
        new UnreachableTypesException([Size: 'enum', Filter: 'input']).code() == BindingCode.UNREACHABLE_VALUE_TYPE
        new UnreachableTypesException([Size: 'enum', Orphan: 'type']).code() == BindingCode.UNREACHABLE_TYPE
    }

    void "an exception that holds one check has the code of that check"() {
        expect:
        new UnmappedInputPropertiesException([:]).code() == BindingCode.UNMAPPED_INPUT_PROPERTY
        new UnwiredDirectivesException([auth: ['Query.me']], [:]).code() == BindingCode.UNWIRED_DIRECTIVE
        new UnwiredDirectivesException([:], [gone: 'not declared']).code() == BindingCode.INVALID_INERT_DIRECTIVES
        new UnwiredDirectivesException([:], [:], [gone: 'not declared']).code() == BindingCode.INVALID_SCHEMA_CUSTOMIZER_DIRECTIVES
    }

    void "an aggregate has a code of its own and tags each finding with the code of the finding"() {
        given:
        def strict = new StrictModeException([BindingCode.UNBOUND_ENUM_CONSTANT, BindingCode.UNUSED_DICTIONARY_ENTRY],
                ['The enum constant a#B has no value.', 'The entry maps Ghost.'])
        def failures = new BindingFailuresException([strict, new UnreachableTypesException([Orphan: 'type'])])

        expect:
        strict.code() == BindingCode.STRICT_FINDINGS
        strict.message.contains('\n  1) [unbound-enum-constant] The enum constant a#B has no value.')
        strict.message.contains('\n  2) [unused-dictionary-entry] The entry maps Ghost.')
        failures.code() == BindingCode.BINDING_FAILURES
        failures.failures*.code() == [BindingCode.STRICT_FINDINGS, BindingCode.UNREACHABLE_TYPE]
        failures.message.contains('\n  2) [unreachable-type] ')
    }
}
