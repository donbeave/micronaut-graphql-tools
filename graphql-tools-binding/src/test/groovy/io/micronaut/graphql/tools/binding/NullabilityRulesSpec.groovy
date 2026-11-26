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

import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import io.micronaut.graphql.tools.binding.internal.model.Nullability
import io.micronaut.graphql.tools.binding.internal.model.TypeRef
import io.micronaut.graphql.tools.binding.internal.rules.NullabilityRules
import io.micronaut.graphql.tools.binding.internal.rules.ReturnVerdict
import spock.lang.Specification
import spock.lang.Unroll

class NullabilityRulesSpec extends Specification {

    static final Type<?> NON_NULL = new NonNullType(new TypeName('String'))
    static final Type<?> NULLABLE = new TypeName('String')
    static final String MISMATCH = 'kotlin-nullability-mismatch'
    static final String LOOSE = 'kotlin-nullability-loose'

    private static TypeRef kotlin(Nullability nullability, boolean primitive = false, TypeRef... arguments) {
        new TypeRef(primitive ? 'int' : 'java.lang.String', arguments as List, nullability, primitive, false, true)
    }

    private static ReturnVerdict plain(int depth = 0, ReturnVerdict element = null) {
        new ReturnVerdict(depth, false, null, ReturnVerdict.Reactive.NONE, false, element)
    }

    @Unroll
    void "an input declared #declared at #schema is #code"() {
        expect:
        NullabilityRules.input(kotlin(declared), schema, 'argument')?.code()?.getCode() == code

        where:
        declared             | schema   | code
        Nullability.NON_NULL | NULLABLE | MISMATCH
        Nullability.NULLABLE | NON_NULL | LOOSE
        Nullability.NON_NULL | NON_NULL | null
        Nullability.NULLABLE | NULLABLE | null
        Nullability.UNKNOWN  | NON_NULL | null
    }

    @Unroll
    void "a return declared #declared at #schema is #code"() {
        expect:
        NullabilityRules.returned(kotlin(declared), schema, plain())*.code()*.getCode() == (code ? [code] : [])

        where:
        declared             | schema   | code
        Nullability.NULLABLE | NON_NULL | MISMATCH
        Nullability.NON_NULL | NULLABLE | LOOSE
        Nullability.NON_NULL | NON_NULL | null
        Nullability.NULLABLE | NULLABLE | null
    }

    void "a finding names what is wrong and how to fix it"() {
        when:
        def mismatch = NullabilityRules.returned(kotlin(Nullability.NULLABLE), NON_NULL, plain()).first()

        then:
        mismatch.sentence().contains('String?')
        mismatch.sentence().contains('String!')
        mismatch.fix().contains('non-null')
    }

    void "a type that Java declares is never compared, whatever its annotation says"() {
        given:
        def java = new TypeRef('java.lang.String', [], Nullability.NULLABLE, false, false)

        expect:
        NullabilityRules.input(java, NON_NULL, 'argument') == null
        NullabilityRules.returned(java, NON_NULL, plain()).isEmpty()
    }

    void "a primitive input is left to nullable-primitive"() {
        expect:
        NullabilityRules.input(kotlin(Nullability.NON_NULL, true), NULLABLE, 'argument') == null
    }

    void "a wrapper that can be empty makes the position nullable, so the value inside says nothing"() {
        given:
        def optional = new TypeRef('java.util.Optional', [kotlin(Nullability.NON_NULL)], Nullability.NON_NULL, false, false, true)

        expect:
        NullabilityRules.returned(optional, NULLABLE, plain(1)).isEmpty()
    }

    void "a future passes the value through"() {
        given:
        def future = new TypeRef('java.util.concurrent.CompletableFuture', [kotlin(Nullability.NULLABLE)], Nullability.NON_NULL,
                false, false, true)

        expect:
        NullabilityRules.returned(future, NON_NULL, plain(1))*.code()*.getCode() == [MISMATCH]
    }

    void "the element of a list is compared with the element of the list type"() {
        given:
        def list = new TypeRef('java.util.List', [kotlin(Nullability.NULLABLE)], Nullability.NON_NULL, false, false, true)
        Type<?> schema = new NonNullType(new ListType(NON_NULL))

        expect:
        NullabilityRules.returned(list, schema, plain(0, plain()))*.code()*.getCode() == [MISMATCH]
        NullabilityRules.returned(list, new NonNullType(new ListType(NULLABLE)), plain(0, plain())).isEmpty()
    }
}
