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

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** The bytecode of this module obeys the rules that the library has, and reaches the execution state through the context only. */
class SecurityArchitectureSpec extends Specification {

    static final String ROOT = 'io.micronaut.graphql.tools.security'

    @Shared JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT)

    void "the classes of the module are found"() {
        expect:
        classes.contain('io.micronaut.graphql.tools.security.GraphQLSecurity')
        classes.contain('io.micronaut.graphql.tools.security.internal.SecuredFieldDecorator')
    }

    void "no class depends on Jackson"() {
        expect:
        noClasses().should().dependOnClassesThat(resideInAPackage('com.fasterxml.jackson..')).check(classes)
    }

    void "the public package depends on no internal package"() {
        expect:
        noClasses().that().resideInAPackage(ROOT).should().dependOnClassesThat(resideInAPackage(ROOT + '.internal..')).check(classes)
    }

    void "no class depends on an internal package of the library: the module uses its public SPI"() {
        expect:
        noClasses().should().dependOnClassesThat(resideInAPackage('io.micronaut.graphql.tools.internal..')).check(classes)
    }

    void "no class reads a thread-local request: the principal comes from the context of the execution"() {
        expect:
        noClasses().should().dependOnClassesThat(resideInAPackage('io.micronaut.http.context..')).check(classes)
    }

    void "every public extension point interface starts with GraphQL"() {
        given:
        def suffixes = ['Customizer', 'Decorator', 'Contributor', 'Interceptor', 'Mapper', 'Hook']
        def extensionPoints = classes.findAll {
            it.interface && it.modifiers.contains(JavaModifier.PUBLIC) && !it.packageName.contains('.internal') &&
                    it.methods.any { m -> m.modifiers.contains(JavaModifier.ABSTRACT) } && suffixes.any { s -> it.simpleName.endsWith(s) }
        }

        expect:
        !extensionPoints.empty
        extensionPoints.every { it.simpleName.startsWith('GraphQL') }
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
