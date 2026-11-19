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
package io.micronaut.graphql.tools.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaMember
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import spock.lang.Shared
import spock.lang.Specification

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * The bytecode of the library and the binding module (not the processor, which runs at compile time) obeys three rules:
 * no reflection outside a short allowlist, no Jackson, and no internal type in the signature of the public API. The rules
 * keep the native image free of reflection metadata and the public API free of types that can change in any release.
 */
class ArchitectureRulesSpec extends Specification {

    static final String ROOT = 'io.micronaut.graphql.tools'
    static final String INTERNAL = 'io.micronaut.core.annotation.Internal'

    /**
     * A class that still reads reflectively needs an entry here, with the reason and the issue that removes it. The list
     * may only shrink: an entry whose class no longer reflects fails the spec, so delete it with the fix.
     */
    static final Map<String, String> REFLECTION_ALLOWLIST = [
            'io.micronaut.graphql.tools.GraphQLResolversRegistry': 'reads @GraphQLTypeResolver from the user class with getDeclaredAnnotation, removed by #34',
    ]

    /** The members of java.lang.Class that look up user classes, members or annotations at run time. */
    static final Set<String> REFLECTIVE_CLASS_METHODS = [
            'forName', 'newInstance', 'getMethod', 'getMethods', 'getDeclaredMethod', 'getDeclaredMethods',
            'getField', 'getFields', 'getDeclaredField', 'getDeclaredFields', 'getConstructor', 'getConstructors',
            'getDeclaredConstructor', 'getDeclaredConstructors', 'getAnnotation', 'getAnnotations', 'getDeclaredAnnotation',
            'getDeclaredAnnotations', 'getAnnotationsByType', 'getDeclaredAnnotationsByType', 'getEnumConstants',
            'getRecordComponents', 'getClasses', 'getDeclaredClasses', 'getPermittedSubclasses', 'getEnclosingMethod',
            'getEnclosingConstructor', 'getGenericSuperclass', 'getGenericInterfaces',
    ] as Set

    @Shared JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT)

    void "the classes of the library and the binding module are found"() {
        expect:
        classes.contain('io.micronaut.graphql.tools.GraphQLFactory')
        classes.contain('io.micronaut.graphql.tools.binding.BindingCode')
        classes.size() > 200
    }

    void "no class reflects outside the allowlist"() {
        expect:
        noClasses().that(notAllowlisted()).should(reflects())
                .because('reflection needs native-image metadata and is slower than the introspection the library is built on')
                .check(classes)
    }

    void "the reflection rule fails on a class that reflects and names it"() {
        given:
        def result = noClasses().should(reflects()).evaluate(classes)

        expect:
        result.hasViolation()
        result.failureReport.details.any { it.contains('GraphQLResolversRegistry') && it.contains('getDeclaredAnnotation') }
    }

    void "every allowlist entry names a class that still reflects, a reason and an issue"() {
        given:
        def stale = REFLECTION_ALLOWLIST.keySet().findAll { name ->
            !noClasses().that().haveFullyQualifiedName(name).should(reflects()).evaluate(classes).hasViolation()
        }

        expect:
        assert stale.empty: "No longer reflects, remove it from REFLECTION_ALLOWLIST: ${stale.toSorted()}"
        REFLECTION_ALLOWLIST.every { name, reason -> classes.contain(name) && reason =~ /#\d+/ }
    }

    void "no class depends on Jackson"() {
        expect:
        noClasses().should().dependOnClassesThat(resideInAPackage('com.fasterxml.jackson..'))
                .because('the library uses Micronaut Serialization and bean introspection, never Jackson (#36)')
                .check(classes)
    }

    void "the public API exposes no internal type in a public or protected signature"() {
        expect:
        classes().that(publicApi()).should(exposeNoInternalType())
                .because('a public signature that names an @Internal type or a type of an internal package ties users to code that changes in any release')
                .check(classes)
    }

    private static DescribedPredicate<JavaClass> notAllowlisted() {
        DescribedPredicate.describe('not on the reflection allowlist', { JavaClass c -> !REFLECTION_ALLOWLIST.containsKey(c.name) })
    }

    private static ArchCondition<JavaClass> reflects() {
        new ArchCondition<JavaClass>('reflect (java.lang.Class lookups, java.lang.reflect other than Modifier, java.lang.invoke)') {
            @Override
            void check(JavaClass item, ConditionEvents events) {
                (item.methodCallsFromSelf + item.methodReferencesFromSelf).each { JavaCodeUnitAccess call ->
                    String owner = call.target.owner.name
                    boolean lookup = owner == 'java.lang.Class' && REFLECTIVE_CLASS_METHODS.contains(call.target.name)
                    boolean accessible = call.target.owner.isAssignableTo('java.lang.reflect.AccessibleObject') &&
                            (call.target.name == 'setAccessible' || call.target.name == 'trySetAccessible')
                    if (lookup || accessible) {
                        events.add(SimpleConditionEvent.satisfied(item, "${item.name} calls ${owner}.${call.target.name} (${call.sourceCodeLocation})"))
                    }
                }
                item.directDependenciesFromSelf.each { dependency ->
                    String target = dependency.targetClass.name
                    boolean reflect = dependency.targetClass.packageName == 'java.lang.reflect' && target != 'java.lang.reflect.Modifier'
                    boolean invoke = dependency.targetClass.packageName == 'java.lang.invoke'
                    if (reflect || invoke) {
                        events.add(SimpleConditionEvent.satisfied(item, "${item.name} uses ${target} (${dependency.description})"))
                    }
                }
            }
        }
    }

    private static DescribedPredicate<JavaClass> publicApi() {
        DescribedPredicate.describe('public API classes: public, not @Internal, not generated, not in an internal package', { JavaClass c ->
            c.modifiers.contains(JavaModifier.PUBLIC) && !generated(c) && !isInternal(c) && !isInternal(c.enclosingClass.orElse(null))
        })
    }

    private static boolean generated(JavaClass c) {
        c.name.substring(c.packageName.length() + (c.packageName.empty ? 0 : 1)).startsWith('$')
    }

    private static boolean isInternal(JavaClass c) {
        if (c != null && c.array) {
            return isInternal(c.baseComponentType)
        }
        c != null && (c.isAnnotatedWith(INTERNAL) || (c.name.startsWith(ROOT + '.') && c.packageName.contains('.internal')))
    }

    private static ArchCondition<JavaClass> exposeNoInternalType() {
        new ArchCondition<JavaClass>('expose no internal type in a public or protected signature') {
            @Override
            void check(JavaClass item, ConditionEvents events) {
                (item.superclass.map { [it] }.orElse([]) + item.interfaces).collectMany { it.allInvolvedRawTypes }.findAll { isInternal(it) }.each { JavaClass t ->
                    events.add(SimpleConditionEvent.violated(item, "${item.name} extends or implements the internal type ${t.name}"))
                }
                List<JavaMember> members = []
                members.addAll(item.fields)
                members.addAll(item.methods)
                members.addAll(item.constructors)
                members.findAll { m -> m.modifiers.any { it.name() in ['PUBLIC', 'PROTECTED'] } }.each { JavaMember m ->
                    Set<JavaClass> types = m instanceof JavaField ? m.type.allInvolvedRawTypes : ((JavaCodeUnit) m).allInvolvedRawTypes
                    types.findAll { isInternal(it) }.each { JavaClass t ->
                        events.add(SimpleConditionEvent.violated(m, "${m.fullName} exposes the internal type ${t.name}"))
                    }
                }
            }
        }
    }
}
