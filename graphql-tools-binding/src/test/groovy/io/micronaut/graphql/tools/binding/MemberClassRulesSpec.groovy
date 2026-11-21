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

import io.micronaut.graphql.tools.binding.internal.membership.MemberClassRules
import io.micronaut.graphql.tools.binding.internal.membership.MemberClassRules.AbstractReturn
import io.micronaut.graphql.tools.binding.internal.membership.MemberClassRules.MemberClass
import spock.lang.Specification

class MemberClassRulesSpec extends Specification {

    private static AbstractReturn returned(String... members) {
        new AbstractReturn('example.Query#search()', 'Query.search', null, true, 'SearchResult', 'example.SearchResult',
                members.toList(), null)
    }

    private static Optional<MemberClass> member(String name, boolean finalClass, String... supertypes) {
        Optional.of(new MemberClass(name, finalClass, supertypes.toList().toSet()))
    }

    void "a final class that does not implement the declared interface is disjoint from it"() {
        when:
        def finding = MemberClassRules.check(returned('Book'), { member('example.Book', true) })

        then:
        finding.get().code() == 'incorrect-member-mapping'
        finding.get().coordinate() == 'Query.search'
    }

    void "a class that implements the interface, or can be subclassed, is not disjoint"() {
        expect:
        MemberClassRules.check(returned('Book'), { member('example.Book', true, 'example.SearchResult') }).empty
        MemberClassRules.check(returned('Book'), { member('example.Book', false) }).empty
    }

    void "the first disjoint member is the finding, and a member without a known class ends the walk"() {
        given:
        def classes = [Book: member('example.Book', false), Magazine: member('example.Magazine', true),
                       Poster: member('example.Poster', true)]

        expect:
        MemberClassRules.check(returned('Book', 'Magazine', 'Poster'), { classes[it] }).get().sentence().contains('Magazine')
        MemberClassRules.check(returned('Unknown', 'Magazine'), { classes.getOrDefault(it, Optional.empty()) }).empty
    }

}
