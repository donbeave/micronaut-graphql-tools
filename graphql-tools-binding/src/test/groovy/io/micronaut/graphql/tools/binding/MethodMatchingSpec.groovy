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
package io.micronaut.graphql.tools.binding

import io.micronaut.graphql.tools.binding.internal.model.AnnotationNames
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules
import spock.lang.Specification

import static io.micronaut.graphql.tools.binding.FakeMethods.IGNORED
import static io.micronaut.graphql.tools.binding.FakeMethods.coordinate
import static io.micronaut.graphql.tools.binding.FakeMethods.method

class MethodMatchingSpec extends Specification {

    void "a method binds the field of its name"() {
        expect:
        ResolverClassRules.bindsField(method('book'), 'book', ['Query.book'] as Set, null, false)
        !ResolverClassRules.bindsField(method('books'), 'book', ['Query.book'] as Set, null, false)
    }

    void "with accessor prefixes a method binds the field its get or is prefix leaves, besides its own"() {
        expect:
        ResolverClassRules.bindsField(method('getAuthor'), 'author', ['Book.author'] as Set, null, true)
        ResolverClassRules.bindsField(method('getAuthor'), 'getAuthor', ['Book.getAuthor'] as Set, null, true)
        ResolverClassRules.bindsField(method('isActive'), 'active', ['Book.active'] as Set, null, true)
        !ResolverClassRules.bindsField(method('getAuthor'), 'author', ['Book.author'] as Set, null, false)
        !ResolverClassRules.bindsField(method('author'), 'getAuthor', ['Book.getAuthor'] as Set, null, true)
    }

    void "a declared coordinate names the field whatever the name of the method is"() {
        given:
        def declared = ['a.R#klass()': 'Book.class']
        def klass = method('klass', coordinate('Book.class'), [], null, 'a.R#klass()')

        expect:
        ResolverClassRules.bindsField(klass, 'class', ['Book.class'] as Set, declared, false)
        !ResolverClassRules.bindsField(klass, 'klass', ['Book.klass'] as Set, declared, false)
        ResolverClassRules.boundNames(klass, declared, true) == ['class']
        ResolverClassRules.boundNames(method('getA'), null, true) == ['getA', 'a']
        ResolverClassRules.boundNames(method('getA'), null, false) == ['getA']
        ResolverClassRules.boundNames(method('getA', IGNORED), null, true) == []
    }

    void "an ignored method binds nothing"() {
        expect:
        !ResolverClassRules.bindsField(method('book', IGNORED), 'book', ['Query.book'] as Set, null, false)
        !ResolverClassRules.isCandidate(method('book', IGNORED), null)
    }

    void "in a class where a method declares a coordinate, a method binds the coordinates it declares"() {
        given:
        def declared = ['a.R#book()': 'Query.book', 'a.R#other()': 'Book.other']

        expect:
        ResolverClassRules.bindsField(method('book', coordinate('Query.book')), 'book', ['Query.book'] as Set, declared, false)
        ResolverClassRules.bindsField(method('book', coordinate('Query.book')), 'book', ['Query.book', 'Node.book'] as Set, declared, false)
        !ResolverClassRules.bindsField(method('book', coordinate('Book.other')), 'book', ['Query.book'] as Set,
                ['a.R#book()': 'Book.other'], false)
    }

    void "in such a class a method without a coordinate is no candidate and binds nothing"() {
        given:
        def declared = ['a.R#book()': 'Query.book']

        expect:
        !ResolverClassRules.isCandidate(method('other', [:], [], null, 'a.R#other()'), declared)
        !ResolverClassRules.bindsField(method('other', [:], [], null, 'a.R#other()'), 'other', ['Query.other'] as Set, declared, false)
        ResolverClassRules.isCandidate(method('book'), declared)
        ResolverClassRules.isCandidate(method('other'), null)
    }

    void "the declared coordinates skip an ignored method and are null when there is none"() {
        expect:
        ResolverClassRules.declaredCoordinates([method('a'), method('b', IGNORED + coordinate('Query.b'))]) == null
        ResolverClassRules.declaredCoordinates([method('a', coordinate('Query.a'), [], null, 'x#a()'), method('b')]) ==
                ['x#a()': 'Query.a']
        ResolverClassRules.declaredCoordinate(method('a')) == ''
        ResolverClassRules.declaredCoordinate(method('a', coordinate('Query.a'))) == 'Query.a'
    }

    void "a lifecycle callback is never a candidate for a field"() {
        expect:
        ResolverClassRules.isLifecycle(method('onStart', [(name): [:]]))
        !ResolverClassRules.isLifecycle(method('book', [(AnnotationNames.IGNORE): [:]]))

        where:
        name << ['io.micronaut.runtime.event.annotation.EventListener', 'io.micronaut.scheduling.annotation.Scheduled',
                 'io.micronaut.scheduling.annotation.Schedules', 'jakarta.annotation.PreDestroy']
    }
}
