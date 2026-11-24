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
package io.micronaut.graphql.tools.testing

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import spock.lang.Shared
import spock.lang.Specification

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** The bytecode of this module obeys the rules that the library has, and it pulls in no test framework. */
class TesterArchitectureSpec extends Specification {

    static final String ROOT = 'io.micronaut.graphql.tools.testing'

    @Shared JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT)

    void "the classes of the module are found"() {
        expect:
        classes.contain('io.micronaut.graphql.tools.testing.GraphQLTester')
        classes.contain('io.micronaut.graphql.tools.testing.internal.TestContextContributor')
    }

    void "no class depends on Jackson or on a test framework"() {
        expect:
        noClasses().should().dependOnClassesThat(resideInAPackage('com.fasterxml.jackson..')).check(classes)
        noClasses().should().dependOnClassesThat(resideInAPackage('org.junit..')).check(classes)
        noClasses().should().dependOnClassesThat(resideInAPackage('spock..')).check(classes)
    }

    void "the public package depends on no internal package"() {
        expect:
        noClasses().that().resideInAPackage(ROOT).should().dependOnClassesThat(resideInAPackage(ROOT + '.internal..')).check(classes)
    }

}
