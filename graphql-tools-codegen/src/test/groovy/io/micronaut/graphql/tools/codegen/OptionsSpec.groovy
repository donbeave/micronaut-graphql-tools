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
package io.micronaut.graphql.tools.codegen

import spock.lang.Specification
import spock.lang.Unroll

class OptionsSpec extends Specification {

    def "the defaults are the ones of the design"() {
        when:
        def o = Options.builder().packageName('com.example').build()

        then:
        o.language() == Language.JAVA
        o.mode() == Mode.SERVER
        o.modelStyle() == ModelStyle.RECORD
        o.nameConstants() == NameConstants.FIELDS
        o.generatedAnnotation() == 'javax.annotation.processing.Generated'
        o.nullableAnnotation() == 'io.micronaut.core.annotation.Nullable'
        o.rootContracts()
        o.contractNameTemplate() == '{Field}{Operation}Resolver'
        !o.kotlinSuspend()
        o.subscriptionReturnType() == 'org.reactivestreams.Publisher'
        o.includes() == ['**/*.graphqls', '**/*.graphql']
        o.excludes().empty
        o.interfaceTypes().empty
        o.typeMappings().isEmpty()
        !o.commentsAsDescriptions()
        o.effectiveModelPackage() == 'com.example.model'
        o.effectiveResolverPackage() == 'com.example.resolver'
        o.effectiveNamesPackage() == 'com.example.names'
        o.validate().empty
    }

    def "the client mode defaults the nullable annotation to JSpecify"() {
        expect:
        Options.builder().packageName('p').mode(Mode.CLIENT).build().nullableAnnotation() == 'org.jspecify.annotations.Nullable'
        Options.builder().packageName('p').mode(Mode.CLIENT).nullableAnnotation('a.B').build().nullableAnnotation() == 'a.B'
    }

    def "the string form of the defaults lists only the package name"() {
        expect:
        Options.builder().packageName('com.example').build().toMap() == [packageName: 'com.example']
        Options.builder().build().toMap() == [:]
    }

    def "the string form lists only non-default values, in key order"() {
        when:
        def map = Options.builder().packageName('com.example').language(Language.KOTLIN).kotlinSuspend(true)
                .typeMappings([Date: 'java.time.LocalDate', 'Query.now': 'java.time.Instant']).includes(['a/*.graphqls'])
                .excludes(['x']).build().toMap()

        then:
        map.keySet() as List == ['excludes.0', 'excludes.size', 'includes.0', 'includes.size', 'kotlinSuspend', 'language',
                                 'packageName', 'typeMappings.Date', 'typeMappings.Query.now']
        map['excludes.size'] == '1'
        map['kotlinSuspend'] == 'true'
        map['language'] == 'KOTLIN'
        map['typeMappings.Query.now'] == 'java.time.Instant'
    }

    def "the string form is immutable"() {
        when:
        Options.builder().packageName('p').build().toMap().put('x', 'y')

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll
    def "the round trip through the string form is lossless: #name"() {
        expect:
        def result = Options.fromMap(options.toMap())
        result.diagnostics().empty
        result.options() == options

        where:
        name                       | options
        'defaults'                 | Options.builder().build()
        'package only'             | Options.builder().packageName('com.example').build()
        'everything changed'       | everything()
        'client with server nullable' | Options.builder().mode(Mode.CLIENT).nullableAnnotation('io.micronaut.core.annotation.Nullable').build()
        'server with client nullable' | Options.builder().nullableAnnotation('org.jspecify.annotations.Nullable').build()
        'client, nullable none'    | Options.builder().mode(Mode.CLIENT).nullableAnnotation('').build()
        'empty includes'           | Options.builder().includes([]).build()
        'empty element and spaces' | Options.builder().excludes(['', ' a b ']).interfaceTypes(['A']).build()
        'an empty mapping key'     | Options.builder().typeMappings(['': 'a.B']).build()
        'a key that looks like a list' | Options.builder().typeMappings(['size': 'a.B', '0': 'a.C']).build()
    }

    def "the round trip holds for every combination of the enums and booleans"() {
        expect:
        [Language.values(), Mode.values(), ModelStyle.values(), NameConstants.values(), [true, false], [true, false]].combinations().every {
            def o = Options.builder().packageName('p').language(it[0]).mode(it[1]).modelStyle(it[2]).nameConstants(it[3])
                    .rootContracts(it[4]).commentsAsDescriptions(it[5]).build()
            Options.fromMap(o.toMap()).options() == o
        }
    }

    def "the options are immutable"() {
        given:
        def list = ['a']
        def map = [A: 'a.B']
        def o = Options.builder().includes(list).typeMappings(map).build()

        when:
        list << 'b'
        map.C = 'c.D'

        then:
        o.includes() == ['a']
        o.typeMappings() == [A: 'a.B']

        when:
        o.includes().add('x')

        then:
        thrown(UnsupportedOperationException)

        when:
        o.typeMappings().put('x', 'y')

        then:
        thrown(UnsupportedOperationException)
    }

    def "toBuilder keeps every value"() {
        expect:
        everything().toBuilder().build() == everything()
    }

    def "an absent key has its default and the mode decides the nullable default"() {
        expect:
        Options.fromMap([mode: 'CLIENT']).options().nullableAnnotation() == 'org.jspecify.annotations.Nullable'
        Options.fromMap([:]).options() == Options.builder().build()
    }

    @Unroll
    def "fromMap names the option of #key = #value"() {
        when:
        def result = Options.fromMap([(key): value])

        then:
        result.hasErrors()
        result.diagnostics().size() == 1
        result.diagnostics()[0].optionName() == key
        result.diagnostics()[0].code() == code
        result.diagnostics()[0].message().contains(key)

        where:
        key                | value    | code
        'language'         | 'SCALA'  | Diagnostic.INVALID_OPTION_VALUE
        'mode'             | 'both'   | Diagnostic.INVALID_OPTION_VALUE
        'modelStyle'       | ''       | Diagnostic.INVALID_OPTION_VALUE
        'nameConstants'    | 'ALL'    | Diagnostic.INVALID_OPTION_VALUE
        'rootContracts'    | 'yes'    | Diagnostic.INVALID_OPTION_VALUE
        'kotlinSuspend'    | 'TRUE'   | Diagnostic.INVALID_OPTION_VALUE
        'includes.size'    | 'many'   | Diagnostic.INVALID_OPTION_VALUE
        'excludes.size'    | '-1'     | Diagnostic.INVALID_OPTION_VALUE
        'packge'           | 'a.b'    | Diagnostic.UNKNOWN_OPTION
        'typeMappings'     | 'a.B'    | Diagnostic.UNKNOWN_OPTION
    }

    def "an invalid enum value lists the valid ones"() {
        expect:
        Options.fromMap([language: 'SCALA']).diagnostics()[0].message().contains('[JAVA, KOTLIN, GROOVY]')
    }

    def "a list with a missing element names the element, and an element past the size is unknown"() {
        when:
        def result = Options.fromMap(['includes.size': '2', 'includes.0': 'a', 'excludes.size': '0', 'excludes.0': 'x'])

        then:
        result.diagnostics()*.optionName() == ['includes.1', 'excludes.0']
        result.diagnostics()*.code() == [Diagnostic.MISSING_OPTION, Diagnostic.UNKNOWN_OPTION]
        result.options().includes() == Options.DEFAULT_INCLUDES
    }

    def "list elements without a size name the size"() {
        when:
        def result = Options.fromMap(['includes.0': 'a'])

        then:
        result.diagnostics()*.optionName() == ['includes.size']
        result.diagnostics()*.code() == [Diagnostic.MISSING_OPTION]
    }

    def "a value that is invalid keeps the default and the others are read"() {
        when:
        def result = Options.fromMap([language: 'SCALA', packageName: 'p', modelStyle: 'BEAN'])

        then:
        result.options().language() == Language.JAVA
        result.options().packageName() == 'p'
        result.options().modelStyle() == ModelStyle.BEAN
        result.diagnostics().size() == 1
    }

    def "unknown keys are reported in key order"() {
        expect:
        Options.fromMap([zeta: '1', alpha: '2']).diagnostics()*.optionName() == ['alpha', 'zeta']
    }

    def "a null value is a diagnostic, not an exception"() {
        when:
        def result = Options.fromMap([language: null])

        then:
        result.diagnostics()*.optionName() == ['language']
    }

    @Unroll
    def "validate reports #option for #changed"() {
        when:
        def diagnostics = changed.validate()

        then:
        diagnostics*.optionName() == [option]
        diagnostics[0].isError()
        diagnostics[0].message().contains(option)

        where:
        changed                                                                            | option
        Options.builder().build()                                                          | 'packageName'
        base().packageName('com.1bad').build()                                             | 'packageName'
        base().packageName('com.class.x').build()                                          | 'packageName'
        base().modelPackage('a..b').build()                                                | 'modelPackage'
        base().resolverPackage('not valid').build()                                        | 'resolverPackage'
        base().namesPackage('a-b').build()                                                 | 'namesPackage'
        base().generatedAnnotation('a.').build()                                           | 'generatedAnnotation'
        base().nullableAnnotation('1').build()                                             | 'nullableAnnotation'
        base().subscriptionReturnType('').build()                                          | 'subscriptionReturnType'
        base().typeNamePrefix('a b').build()                                               | 'typeNamePrefix'
        base().typeNamePrefix('1').build()                                                 | 'typeNamePrefix'
        base().typeMappings(['': 'a.B']).build()                                           | 'typeMappings.'
        base().typeNameSuffix('x-').build()                                                | 'typeNameSuffix'
        base().interfaceTypes(['Ok', '1x']).build()                                        | 'interfaceTypes.1'
        base().contractNameTemplate('{Fieldd}Resolver').build()                            | 'contractNameTemplate'
        base().contractNameTemplate('1{Field}').build()                                    | 'contractNameTemplate'
        base().contractNameTemplate('').build()                                            | 'contractNameTemplate'
        base().typeMappings(['A.b.c': 'x.Y']).build()                                      | 'typeMappings.A.b.c'
        base().typeMappings([Date: 'not a class']).build()                                 | 'typeMappings.Date'
        base().includes([]).build()                                                        | 'includes'
        base().excludes(['  ']).build()                                                    | 'excludes.0'
        base().kotlinSuspend(true).build()                                                 | 'kotlinSuspend'
        base().kotlinSuspend(true).language(Language.GROOVY).build()                       | 'kotlinSuspend'
        base().mode(Mode.CLIENT).language(Language.KOTLIN).build()                         | 'mode'
    }

    def "valid options of every language and a full set validate cleanly"() {
        expect:
        everything().validate().empty
        base().language(Language.KOTLIN).kotlinSuspend(true).build().validate().empty
        base().typeMappings([Date: 'java.time.LocalDate', 'Query.now': 'a.b.Now']).contractNameTemplate('{Operation}{Field}').build().validate().empty
        base().nullableAnnotation('').generatedAnnotation('').build().validate().empty
    }

    def "validate reports every problem at once, and never throws for an invalid value"() {
        expect:
        Options.builder().language(Language.JAVA).kotlinSuspend(true).modelPackage('1').build().validate()*.optionName() ==
                ['packageName', 'modelPackage', 'kotlinSuspend']
    }

    def "a null type mapping key or value is rejected when the record is created"() {
        when:
        Options.builder().typeMappings(mapping).build()

        then:
        thrown(NullPointerException)

        where:
        mapping << [[(null): 'a.B'], [A: null]]
    }

    def "a null component is rejected when the record is created"() {
        when:
        Options.builder().packageName(null).build()

        then:
        thrown(NullPointerException)
    }

    private static Options.Builder base() {
        Options.builder().packageName('com.example')
    }

    static Options everything() {
        Options.builder().language(Language.KOTLIN).mode(Mode.SERVER).packageName('com.example.api').modelPackage('com.example.m')
                .resolverPackage('com.example.r').namesPackage('com.example.n').generatedAnnotation('jakarta.annotation.Generated')
                .modelStyle(ModelStyle.BEAN).interfaceTypes(['Node', 'Entity']).typeNamePrefix('Gql').typeNameSuffix('Dto')
                .nullableAnnotation('org.jspecify.annotations.Nullable').rootContracts(false)
                .contractNameTemplate('{Operation}{Field}Contract').kotlinSuspend(true)
                .subscriptionReturnType('kotlinx.coroutines.flow.Flow')
                .typeMappings([Date: 'java.time.LocalDate', 'Query.now': 'java.time.Instant', 'DateTime': 'java.time.OffsetDateTime'])
                .typeMappingsFromDependencies(false)
                .nameConstants(NameConstants.TYPES).includes(['schema/**/*.graphqls']).excludes(['**/draft/**', '**/old.graphqls'])
                .commentsAsDescriptions(true).build()
    }
}
