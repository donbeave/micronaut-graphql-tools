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
package io.micronaut.graphql.tools.micrometer

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** The bytecode of this module obeys the rules that the library has: no Jackson, no reflection on user classes, a public package that names no internal one. */
class MicrometerArchitectureSpec extends Specification {

    static final String ROOT = 'io.micronaut.graphql.tools.micrometer'

    @Shared JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT)

    void "the classes of the module are found"() {
        expect:
        classes.contain('io.micronaut.graphql.tools.micrometer.GraphQLMetrics')
        classes.contain('io.micronaut.graphql.tools.micrometer.internal.MicrometerObserver')
    }

    void "no class depends on Jackson"() {
        expect:
        noClasses().should().dependOnClassesThat(resideInAPackage('com.fasterxml.jackson..')).check(classes)
    }

    void "the public package depends on no internal package"() {
        expect:
        noClasses().that().resideInAPackage(ROOT).should().dependOnClassesThat(resideInAPackage(ROOT + '.internal..')).check(classes)
    }

    void "OpenTelemetry types stay out of the module, and Micrometer types stay in the classes that record"() {
        expect: 'a class loads without OpenTelemetry, so that an application with only Micrometer works'
        noClasses().should().dependOnClassesThat(resideInAPackage('io.opentelemetry..')).check(classes)
        noClasses().that().haveNameNotMatching('.*(Micrometer|GraphQLMetrics).*')
                .should().dependOnClassesThat(resideInAPackage('io.micrometer..')).check(classes)
    }

    void "no class wraps a data fetcher: the field-wrapping classes live in the OpenTelemetry module"() {
        expect:
        noClasses().should().implement(graphql.schema.DataFetcher).check(classes)
        noClasses().should().implement(io.micronaut.graphql.tools.execution.GraphQLFieldFetcherDecorator).check(classes)
    }

    void "no class looks up a class, member or annotation by reflection"() {
        given:
        def reflective = ['forName', 'newInstance', 'getMethod', 'getMethods', 'getDeclaredMethod', 'getDeclaredMethods', 'getField',
                          'getFields', 'getDeclaredField', 'getDeclaredFields', 'getConstructor', 'getConstructors',
                          'getDeclaredConstructor', 'getDeclaredConstructors', 'getAnnotation', 'getAnnotations', 'isAnnotationPresent',
                          'getEnumConstants'] as Set
        def offenders = classes.collectMany { JavaClass c ->
            c.methodCallsFromSelf.findAll { it.target.owner.name == 'java.lang.Class' && it.target.name in reflective }
                    .collect { "${c.name} calls Class.${it.target.name}".toString() }
        }

        expect:
        assert offenders.empty: offenders.join('\n')
    }

}
