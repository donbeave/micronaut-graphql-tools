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
import graphql.language.TypeName
import graphql.schema.idl.SchemaParser
import io.micronaut.graphql.tools.binding.internal.rules.ResolverClassRules
import io.micronaut.graphql.tools.binding.internal.rules.ReturnVerdict
import io.micronaut.graphql.tools.binding.internal.schema.WrapperRules
import spock.lang.Specification

import static io.micronaut.graphql.tools.binding.FakeMethods.OPTIONAL
import static io.micronaut.graphql.tools.binding.FakeMethods.PUBLISHER
import static io.micronaut.graphql.tools.binding.FakeMethods.RESULT
import static io.micronaut.graphql.tools.binding.FakeMethods.STAGE
import static io.micronaut.graphql.tools.binding.FakeMethods.array
import static io.micronaut.graphql.tools.binding.FakeMethods.model
import static io.micronaut.graphql.tools.binding.FakeMethods.primitive
import static io.micronaut.graphql.tools.binding.FakeMethods.ref

class ReturnAdaptationSpec extends Specification {

    static final nullable = new TypeName('Book')
    static final nonNull = new NonNullType(new TypeName('Book'))
    static final STRING = ref('java.lang.String')
    static final MODEL = model(['java.util.concurrent.CompletableFuture': [STAGE], 'reactor.core.publisher.Flux': [PUBLISHER], 'reactor.core.publisher.Mono': [PUBLISHER],
            'io.reactivex.rxjava3.core.Single': [], 'io.reactivex.Maybe': [], 'io.reactivex.rxjava3.core.Completable': [],
            'io.micronaut.core.async.publisher.CompletableFuturePublisher': [PUBLISHER],
            'kotlinx.coroutines.flow.Flow': [], 'kotlinx.coroutines.flow.SharedFlow': ['kotlinx.coroutines.flow.Flow']])

    static final REGISTRY = new SchemaParser().parse('scalar JSON\ntype Book { title: String }')

    private static adapt(declared, type = nullable, boolean fieldLevel = true, boolean subscription = false,
                         boolean singleResult = false, boolean suspend = false) {
        ResolverClassRules.returnAdaptation(MODEL, REGISTRY, declared, type, fieldLevel, subscription, singleResult, suspend)
    }

    void "a type without a wrapper is adapted to itself"() {
        expect:
        adapt(STRING).depth() == 0
        adapt(STRING).failure() == null
        !adapt(STRING).primitiveOptional()
    }

    void "each wrapper that is peeled counts once, in the order CompletionStage, Publisher, DataFetcherResult, Optional"() {
        expect:
        adapt(ref(STAGE, ref(RESULT, ref(OPTIONAL, STRING)))).depth() == 3
        adapt(ref('java.util.concurrent.CompletableFuture', ref(OPTIONAL, STRING))).depth() == 2
        adapt(ref(STAGE, ref(PUBLISHER, STRING)), nullable, true, true).depth() == 2
        adapt(ref('reactor.core.publisher.Flux', STRING), nullable, true, true).depth() == 1
    }

    void "a CompletionStage inside a list is a violation"() {
        expect:
        adapt(ref(STAGE, STRING), nullable, false).failure() == WrapperRules.completionStageInList()
    }

    void "a subscription field must return a Publisher, and no other field may"() {
        expect:
        adapt(STRING, nullable, true, true).failure() == WrapperRules.subscriptionNeedsPublisher('java.lang.String')
        adapt(array('java.lang.String'), nullable, true, true).failure() ==
                WrapperRules.subscriptionNeedsPublisher('[Ljava.lang.String;')
        adapt(array('[Ljava.lang.String;'), nullable, true, true).failure() ==
                WrapperRules.subscriptionNeedsPublisher('[[Ljava.lang.String;')
        adapt(array('int', true), nullable, true, true).failure() == WrapperRules.subscriptionNeedsPublisher('[I')
        adapt(ref(PUBLISHER, STRING)).failure() == WrapperRules.multiValuePublisherNeedsList('Book')
    }

    void "an element of a list is not a field: a Publisher is a violation there even in a subscription"() {
        expect:
        adapt(ref(PUBLISHER, STRING), nullable, false, true).failure() == WrapperRules.publisherInList()
    }

    void "a Publisher outside the subscription type is adapted: one value for a Mono, many for the rest on a list field"() {
        given:
        def list = new ListType(nullable)
        def mono = ref('reactor.core.publisher.Mono', STRING)

        expect:
        adapt(mono).reactive() == ReturnVerdict.Reactive.SINGLE
        adapt(mono).depth() == 1
        adapt(ref(OPTIONAL, STRING)).reactive() == ReturnVerdict.Reactive.NONE
        adapt(ref('reactor.core.publisher.Mono', ref(OPTIONAL, STRING))).depth() == 2
        adapt(ref('reactor.core.publisher.Flux', STRING), list).reactive() == ReturnVerdict.Reactive.MULTI
        adapt(ref('reactor.core.publisher.Flux', STRING), list).depth() == 1
        adapt(ref(PUBLISHER, STRING), new NonNullType(list)).reactive() == ReturnVerdict.Reactive.MULTI
        adapt(ref('reactor.core.publisher.Mono', ref('java.util.List', STRING)), list).reactive() == ReturnVerdict.Reactive.SINGLE
    }

    void "the single-value types are named once: Mono, the RxJava types, CompletableFuturePublisher and a @SingleResult method"() {
        given:
        def list = new ListType(nullable)

        expect:
        adapt(ref('io.reactivex.rxjava3.core.Single', STRING)).reactive() == ReturnVerdict.Reactive.SINGLE
        adapt(ref('io.reactivex.Maybe', STRING)).reactive() == ReturnVerdict.Reactive.SINGLE
        adapt(ref('io.micronaut.core.async.publisher.CompletableFuturePublisher', STRING)).reactive() == ReturnVerdict.Reactive.SINGLE
        adapt(ref('io.micronaut.core.async.publisher.CompletableFuturePublisher', STRING), list).depth() == 1
        // a Completable emits no value and has no type argument: nothing is unwrapped
        adapt(ref('io.reactivex.rxjava3.core.Completable')).failure() == null
        adapt(ref('io.reactivex.rxjava3.core.Completable')).reactive() == ReturnVerdict.Reactive.VALUELESS
        adapt(ref('io.reactivex.rxjava3.core.Completable')).depth() == 0
        adapt(ref('io.reactivex.rxjava3.core.Completable')).async()
        adapt(ref(PUBLISHER, STRING), nullable, true, false, true).reactive() == ReturnVerdict.Reactive.SINGLE
        adapt(ref('reactor.core.publisher.Flux', STRING), list, true, false, true).reactive() == ReturnVerdict.Reactive.SINGLE
        adapt(ref(PUBLISHER, STRING), list).reactive() == ReturnVerdict.Reactive.MULTI
        // an RxJava type is never a stream, and never an element of a list
        adapt(ref('io.reactivex.rxjava3.core.Single', STRING), nullable, true, true).failure() ==
                WrapperRules.subscriptionNeedsPublisher('io.reactivex.rxjava3.core.Single')
        adapt(ref('io.reactivex.rxjava3.core.Single', STRING), nullable, false).failure() == WrapperRules.publisherInList()
    }

    void "a field is async when it returns a CompletionStage or a publisher outside the subscription type"() {
        given:
        def list = new ListType(nullable)

        expect:
        adapt(ref(STAGE, STRING)).async()
        adapt(ref('java.util.concurrent.CompletableFuture', ref(RESULT, STRING))).async()
        adapt(ref('reactor.core.publisher.Mono', STRING)).async()
        adapt(ref('reactor.core.publisher.Flux', STRING), list).async()
        adapt(ref('io.reactivex.rxjava3.core.Single', STRING)).async()
        !adapt(STRING).async()
        !adapt(ref(OPTIONAL, STRING)).async()
        !adapt(ref(RESULT, STRING)).async()
        !adapt(ref(PUBLISHER, STRING), nullable, true, true).async()
        !adapt(ref(STAGE, ref(PUBLISHER, STRING)), nullable, true, true).async()
    }

    void "a Publisher of many values needs a list field, and a Publisher never sits inside a CompletionStage"() {
        expect:
        adapt(ref('reactor.core.publisher.Flux', STRING)).failure() == WrapperRules.multiValuePublisherNeedsList('Book')
        adapt(ref('reactor.core.publisher.Flux', STRING), nonNull).failure() ==
                WrapperRules.multiValuePublisherNeedsList('Book!')
        adapt(ref(STAGE, ref('reactor.core.publisher.Mono', STRING))).failure() == WrapperRules.publisherInCompletionStage()
        adapt(ref('reactor.core.publisher.Mono')).failure() == WrapperRules.missingTypeArgument(PUBLISHER)
        adapt(ref('reactor.core.publisher.Mono', ref('reactor.core.publisher.Mono', STRING))).failure() ==
                WrapperRules.cannotWrap(PUBLISHER, PUBLISHER)
    }

    void "an Optional needs a nullable GraphQL type"() {
        expect:
        adapt(ref(OPTIONAL, STRING), nonNull).failure() == WrapperRules.optionalAtNonNull('Book!', true)
        adapt(ref(OPTIONAL, STRING), new NonNullType(new ListType(nonNull))).failure() ==
                WrapperRules.optionalAtNonNull('[Book!]!', true)
        adapt(ref(OPTIONAL, STRING), new ListType(nonNull)).failure() == null
    }

    void "a primitive optional is adapted to its boxed type at a nullable position only"() {
        expect:
        adapt(ref('java.util.OptionalInt')).primitiveOptional()
        adapt(ref('java.util.OptionalInt')).failure() == null
        adapt(ref(STAGE, ref('java.util.OptionalLong'))).depth() == 1
        adapt(ref('java.util.OptionalDouble'), nonNull).failure() ==
                WrapperRules.primitiveOptionalAtNonNull('java.util.OptionalDouble', 'Book!', true)
        adapt(ref(OPTIONAL, ref('java.util.OptionalInt'))).failure() ==
                WrapperRules.optionalCannotWrapPrimitiveOptional('java.util.OptionalInt')
    }

    void "a wrapper inside a wrapper that cannot hold it names both"() {
        expect:
        adapt(ref(OPTIONAL, ref(STAGE, STRING))).failure() == WrapperRules.cannotWrap(OPTIONAL, STAGE)
        adapt(ref(RESULT, ref(RESULT, STRING))).failure() == WrapperRules.cannotWrap(RESULT, RESULT)
    }

    void "an explicit Object argument is a raw wrapper only where no custom scalar can take it"() {
        expect:
        adapt(ref(STAGE, ref('java.lang.Object')), nullable).failure() == WrapperRules.missingTypeArgument(STAGE)
        adapt(ref(STAGE, ref('java.lang.Object')), new NonNullType(new ListType(nonNull))).failure() ==
                WrapperRules.missingTypeArgument(STAGE)
        adapt(ref(OPTIONAL, ref('java.lang.Object')), new TypeName('JSON')).failure() == null
        adapt(ref('reactor.core.publisher.Flux', ref('java.lang.Object')), new ListType(new TypeName('JSON'))).failure() == null
        adapt(ref('reactor.core.publisher.Flux', ref('java.lang.Object')), new ListType(new TypeName('Book'))).failure() ==
                WrapperRules.missingTypeArgument(PUBLISHER)
        adapt(ref(OPTIONAL, ref('java.lang.Object')), new TypeName('String')).failure() ==
                WrapperRules.missingTypeArgument(OPTIONAL)
    }

    void "a wrapper without its type argument is a violation"() {
        expect:
        adapt(ref(STAGE)).failure() == WrapperRules.missingTypeArgument(STAGE)
        adapt(ref(PUBLISHER), nullable, true, true).failure() == WrapperRules.missingTypeArgument(PUBLISHER)
        adapt(ref(RESULT)).failure() == WrapperRules.missingTypeArgument(RESULT)
        adapt(ref(OPTIONAL)).failure() == WrapperRules.missingTypeArgument(OPTIONAL)
    }

    void "an array or a primitive is never a wrapper"() {
        expect:
        adapt(array(STAGE)).depth() == 0
        adapt(primitive('int')).depth() == 0
    }

    void "a primitive at a nullable position is flagged, an array of primitives and a non-null position are not"() {
        expect:
        ResolverClassRules.nullablePrimitive(primitive('int'), false)
        !ResolverClassRules.nullablePrimitive(primitive('int'), true)
        !ResolverClassRules.nullablePrimitive(array('int', true), false)
        !ResolverClassRules.nullablePrimitive(ref('java.lang.Integer'), false)
    }

    void "a suspend function is an async field that wraps nothing"() {
        expect:
        adapt(STRING, nullable, true, false, false, true).failure() == null
        adapt(STRING, nullable, true, false, false, true).async()
        adapt(STRING, nullable, true, false, false, true).depth() == 0
        adapt(ref(OPTIONAL, STRING), nullable, true, false, false, true).depth() == 1
        !adapt(STRING).async()
    }

    void "a suspend function cannot also return a stage or a publisher, or be a subscription field"() {
        expect:
        adapt(ref(STAGE, STRING), nullable, true, false, false, true).failure() == WrapperRules.suspendWithAsyncReturn(STAGE)
        adapt(ref('reactor.core.publisher.Mono', STRING), nullable, true, false, false, true).failure() ==
                WrapperRules.suspendWithAsyncReturn('reactor.core.publisher.Mono')
        adapt(ref('reactor.core.publisher.Flux', STRING), nullable, true, true, false, true).failure() == WrapperRules.suspendOnSubscription()
    }

    void "a Kotlin Flow on a subscription field streams like a Publisher"() {
        given:
        def verdict = adapt(ref('kotlinx.coroutines.flow.Flow', STRING), nullable, true, true)

        expect:
        verdict.failure() == null
        verdict.depth() == 1
        !verdict.async()
        adapt(ref('kotlinx.coroutines.flow.SharedFlow', STRING), nullable, true, true).depth() == 1
        adapt(ref('kotlinx.coroutines.flow.Flow', STRING), nullable, true, true).reactive() == ReturnVerdict.Reactive.NONE
    }

    void "a Kotlin Flow is rejected outside the subscription type, in a list, and inside a stage"() {
        expect:
        adapt(ref('kotlinx.coroutines.flow.Flow', STRING)).failure() == WrapperRules.flowOutsideSubscription()
        adapt(ref('kotlinx.coroutines.flow.Flow', STRING), nullable, false, true).failure() == WrapperRules.flowOutsideSubscription()
        adapt(ref(STAGE, ref('kotlinx.coroutines.flow.Flow', STRING)), nullable, true, true).failure() != null
        WrapperRules.flowOutsideSubscription().contains('Fix:')
    }
}
