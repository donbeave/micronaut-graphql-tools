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
package io.micronaut.graphql.tools.opentelemetry

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** The bytecode of this module obeys the rules that the library has: no Jackson, no reflection on user classes, and no Micrometer. */
class OpenTelemetryArchitectureSpec extends Specification {

    static final String ROOT = 'io.micronaut.graphql.tools.opentelemetry'

    @Shared JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT)

    void "the classes of the module are found"() {
        expect:
        classes.contain('io.micronaut.graphql.tools.opentelemetry.internal.RequestSpanObserver')
        classes.contain('io.micronaut.graphql.tools.opentelemetry.internal.FieldSpanDecorator')
    }

    void "no class depends on Jackson"() {
        expect:
        noClasses().should().dependOnClassesThat(resideInAPackage('com.fasterxml.jackson..')).check(classes)
    }

    void "Micrometer types stay out of the module, and OpenTelemetry types stay in the classes that trace"() {
        expect: 'a class loads without Micrometer, so that an application with only OpenTelemetry works'
        noClasses().should().dependOnClassesThat(resideInAPackage('io.micrometer..')).check(classes)
        noClasses().that().haveNameNotMatching('.*(Tracing|Span).*')
                .should().dependOnClassesThat(resideInAPackage('io.opentelemetry..')).check(classes)
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
