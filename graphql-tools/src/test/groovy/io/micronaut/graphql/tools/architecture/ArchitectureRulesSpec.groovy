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
import com.tngtech.archunit.core.domain.JavaMethod
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
 * The bytecode of the library and the binding module (not the processor, which runs at compile time) obeys four rules:
 * no reflection outside a short allowlist, no Jackson, no internal type in the signature of the public API, and no dependency of a public package on an internal one. The rules
 * keep the native image free of reflection metadata and the public API free of types that can change in any release.
 */
class ArchitectureRulesSpec extends Specification {

    static final String ROOT = 'io.micronaut.graphql.tools'
    static final String INTERNAL = 'io.micronaut.core.annotation.Internal'

    /**
     * A class that still reads reflectively needs an entry here: the exact accesses it may make (owner.member), the reason and
     * the issue that removes it. Any other reflective access in that class fails the rule, and the list may only shrink: an
     * access that no longer happens fails the spec, so delete it with the fix.
     */
    static final Map<String, Map<String, Object>> REFLECTION_ALLOWLIST = [
            'io.micronaut.graphql.tools.internal.model.GraphQLResolversRegistry': [
                    accesses: ['java.lang.Class.getDeclaredAnnotation'],
                    reason  : 'reads @GraphQLTypeResolver from the user class, removed by #34',
            ],
    ]

    /** The members of java.lang.Class that look up user classes, members or annotations at run time. */
    static final Set<String> REFLECTIVE_CLASS_METHODS = [
            'forName', 'newInstance', 'getMethod', 'getMethods', 'getDeclaredMethod', 'getDeclaredMethods',
            'getField', 'getFields', 'getDeclaredField', 'getDeclaredFields', 'getConstructor', 'getConstructors',
            'getDeclaredConstructor', 'getDeclaredConstructors', 'getAnnotation', 'getAnnotations', 'isAnnotationPresent', 'getDeclaredAnnotation',
            'getDeclaredAnnotations', 'getAnnotationsByType', 'getDeclaredAnnotationsByType', 'getEnumConstants',
            'getRecordComponents', 'getClasses', 'getDeclaredClasses', 'getNestMembers', 'getNestHost', 'getPermittedSubclasses', 'getEnclosingMethod',
            'getEnclosingConstructor', 'getGenericSuperclass', 'getTypeParameters', 'getGenericInterfaces',
    ] as Set

    @Shared JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT)

    void "the classes of the library and the binding module are found"() {
        expect:
        classes.contain('io.micronaut.graphql.tools.internal.GraphQLFactory')
        classes.contain('io.micronaut.graphql.tools.binding.BindingCode')
        classes.size() > 200
    }

    void "no class reflects outside the allowlist"() {
        expect:
        noClasses().should(reflects(true))
                .because('reflection needs native-image metadata and is slower than the introspection the library is built on')
                .check(classes)
    }

    void "the reflection rule fails on a class that reflects and names it"() {
        given:
        def result = noClasses().should(reflects(false)).evaluate(classes)

        expect:
        result.hasViolation()
        result.failureReport.details.any { it.contains('GraphQLResolversRegistry') && it.contains('getDeclaredAnnotation') }
    }

    void "every allowlisted access still happens, with a reason and an issue"() {
        given:
        def stale = REFLECTION_ALLOWLIST.collectMany { name, entry ->
            def seen = reflectiveAccesses(classes.get(name)).keySet()
            entry.accesses.findAll { !(it in seen) }.collect { "$name $it" }
        }

        expect:
        assert stale.empty: "No longer reflects, remove it from REFLECTION_ALLOWLIST: ${stale.toSorted()}"
        REFLECTION_ALLOWLIST.every { name, entry -> classes.contain(name) && !entry.accesses.empty && entry.reason =~ /#\d+/ }
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

    void "no class of a public package depends on a class of an internal package"() {
        expect:
        noClasses().that(inPublicPackage()).should(dependOnInternalPackage())
                .because('a public type that reaches into an internal package ties the API to code that changes in any release')
                .check(classes)
    }

    void "the internal package rule fails on a class that depends on one and names it"() {
        given:
        def result = noClasses().that(DescribedPredicate.describe('any class', { JavaClass c -> true }))
                .should(dependOnInternalPackage()).evaluate(classes)

        expect:
        result.hasViolation()
        result.failureReport.details.any { it.contains('io.micronaut.graphql.tools.internal') }
    }

    /** The reflective accesses of a class: the key (owner.member or the type used) with a message for each. */
    private static Map<String, List<String>> reflectiveAccesses(JavaClass item) {
        Map<String, List<String>> found = [:].withDefault { [] }
        (item.methodCallsFromSelf + item.methodReferencesFromSelf).each { JavaCodeUnitAccess call ->
            String owner = call.target.owner.name
            boolean lookup = owner == 'java.lang.Class' && REFLECTIVE_CLASS_METHODS.contains(call.target.name)
            boolean accessible = call.target.owner.isAssignableTo('java.lang.reflect.AccessibleObject') &&
                    (call.target.name == 'setAccessible' || call.target.name == 'trySetAccessible')
            if (lookup || accessible) {
                found["${owner}.${call.target.name}".toString()] << "${item.name} calls ${owner}.${call.target.name} (${call.sourceCodeLocation})".toString()
            }
        }
        item.directDependenciesFromSelf.each { dependency ->
            String target = dependency.targetClass.name
            boolean reflect = dependency.targetClass.packageName == 'java.lang.reflect' && target != 'java.lang.reflect.Modifier'
            boolean invoke = dependency.targetClass.packageName == 'java.lang.invoke'
            if (reflect || invoke) {
                found[target] << "${item.name} uses ${target} (${dependency.description})".toString()
            }
        }
        found
    }

    private static ArchCondition<JavaClass> reflects(boolean applyAllowlist = false) {
        new ArchCondition<JavaClass>('reflect (java.lang.Class lookups, java.lang.reflect other than Modifier, java.lang.invoke)') {
            @Override
            void check(JavaClass item, ConditionEvents events) {
                Collection<String> approved = applyAllowlist ? (REFLECTION_ALLOWLIST[item.name]?.accesses ?: []) : []
                reflectiveAccesses(item).each { key, messages ->
                    if (!(key in approved)) {
                        messages.each { events.add(SimpleConditionEvent.satisfied(item, it)) }
                    }
                }
            }
        }
    }

    private static DescribedPredicate<JavaClass> inPublicPackage() {
        DescribedPredicate.describe('classes of a public package: not in an internal package, not annotated @Internal', { JavaClass c ->
            !generated(c) && !internalChain(c)
        })
    }

    private static ArchCondition<JavaClass> dependOnInternalPackage() {
        new ArchCondition<JavaClass>('depend on a class of an internal package') {
            @Override
            void check(JavaClass item, ConditionEvents events) {
                item.directDependenciesFromSelf.findAll {
                    ('internal' in it.targetClass.packageName.split('\\.').toList())
                }.each { events.add(SimpleConditionEvent.satisfied(item, it.description)) }
            }
        }
    }

    private static DescribedPredicate<JavaClass> publicApi() {
        DescribedPredicate.describe('public API classes: public, not @Internal, not generated, not in an internal package', { JavaClass c ->
            visible(c) && !generated(c) && !internalChain(c)
        })
    }

    private static boolean visible(JavaClass c) {
        c.enclosingClass.present
                ? (c.modifiers.contains(JavaModifier.PUBLIC) || c.modifiers.contains(JavaModifier.PROTECTED)) && visible(c.enclosingClass.get())
                : c.modifiers.contains(JavaModifier.PUBLIC)
    }

    private static boolean internalChain(JavaClass c) {
        isInternal(c) || (c.enclosingClass.present && internalChain(c.enclosingClass.get()))
    }

    private static List<JavaClass> classLiterals(Object value) {
        if (value instanceof JavaClass) {
            return [(JavaClass) value]
        }
        if (value instanceof Object[]) {
            return ((Object[]) value).toList().collectMany { classLiterals(it) }
        }
        []
    }

    private static boolean generated(JavaClass c) {
        c.name.substring(c.packageName.length() + (c.packageName.empty ? 0 : 1)).startsWith('$')
    }

    private static boolean isInternal(JavaClass c) {
        if (c != null && c.array) {
            return isInternal(c.baseComponentType)
        }
        c != null && (c.isAnnotatedWith(INTERNAL) || ('internal' in c.packageName.split('\\.').toList()))
    }

    private static ArchCondition<JavaClass> exposeNoInternalType() {
        new ArchCondition<JavaClass>('expose no internal type in a public or protected signature') {
            @Override
            void check(JavaClass item, ConditionEvents events) {
                (item.superclass.map { [it] }.orElse([]) + item.interfaces + item.typeParameters).collectMany { it.allInvolvedRawTypes }
                        .plus((Collection<JavaClass>) item.permittedSubclasses.orElse([])).findAll { isInternal(it) }.each { JavaClass t ->
                    events.add(SimpleConditionEvent.violated(item, "${item.name} declares the internal type ${t.name}"))
                }
                List<JavaMember> members = []
                members.addAll(item.fields)
                members.addAll(item.methods)
                members.addAll(item.constructors)
                members.findAll { m -> m.modifiers.any { it.name() in ['PUBLIC', 'PROTECTED'] } && !m.isAnnotatedWith(INTERNAL) }.each { JavaMember m ->
                    Set<JavaClass> types = m instanceof JavaField ? m.type.allInvolvedRawTypes : (((JavaCodeUnit) m).allInvolvedRawTypes + ((JavaCodeUnit) m).throwsClause.types.collectMany { it.allInvolvedRawTypes })
                    if (m instanceof JavaMethod) {
                        m.defaultValue.ifPresent { types.addAll(classLiterals(it)) }
                    }
                    types.findAll { isInternal(it) }.each { JavaClass t ->
                        events.add(SimpleConditionEvent.violated(m, "${m.fullName} exposes the internal type ${t.name}"))
                    }
                }
            }
        }
    }
}
