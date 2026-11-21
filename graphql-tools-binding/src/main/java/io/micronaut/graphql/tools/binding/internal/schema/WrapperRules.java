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
package io.micronaut.graphql.tools.binding.internal.schema;

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * The rules of the return type wrappers, as the sentences that name a violation. The wrapper order and the wording live
 * here once; the caller decides what a violation throws or reports.
 */
@Internal
public final class WrapperRules {

    /**
     * The binary names of the types that emit at most one value, so a field that returns one gets that value, or
     * {@code null} when it is empty: Reactor's {@code Mono}, the RxJava 3 and RxJava 2 {@code Single}, {@code Maybe} and
     * {@code Completable} (which carry no {@code Publisher} type of their own) and Micronaut's
     * {@code CompletableFuturePublisher}. A subtype of one of them is single-value too. Every other {@code Publisher}
     * may emit many, unless the method is annotated with {@code @SingleResult}.
     */
    public static final List<String> SINGLE_VALUE_TYPES = List.of(
            "reactor.core.publisher.Mono",
            "io.reactivex.rxjava3.core.Single", "io.reactivex.rxjava3.core.Maybe", "io.reactivex.rxjava3.core.Completable",
            "io.reactivex.Single", "io.reactivex.Maybe", "io.reactivex.Completable",
            "io.micronaut.core.async.publisher.CompletableFuturePublisher");

    /**
     * The binary names of the single-value types that carry no value and so have no type argument.
     */
    public static final List<String> VALUELESS_TYPES = List.of("io.reactivex.rxjava3.core.Completable",
            "io.reactivex.Completable");

    private static final String COMPLETION_STAGE = "java.util.concurrent.CompletionStage";
    private static final String PUBLISHER = "org.reactivestreams.Publisher";
    private static final String OPTIONAL = "java.util.Optional";

    private WrapperRules() {
    }

    /**
     * @return the sentence for a {@code CompletionStage} inside a list
     */
    public static String completionStageInList() {
        return COMPLETION_STAGE + " is not allowed inside a list. Fix: return "
                + "CompletionStage<List<T>> instead (for example, combine the element futures with "
                + "CompletableFuture.allOf), or load the elements with a DataLoader.";
    }

    /**
     * @param found the Java type that a subscription field returns
     * @return the sentence for a subscription field that does not return a {@code Publisher}
     */
    public static String subscriptionNeedsPublisher(String found) {
        return "a field of the subscription type must return " + PUBLISHER
                + " (optionally inside a CompletionStage), found " + found + ". Fix: change the return type to "
                + "Publisher<T> (for example Flux<T>).";
    }

    /**
     * @return the sentence for a Kotlin {@code suspend} function on a field of the subscription type
     */
    public static String suspendOnSubscription() {
        return "a Kotlin suspend function cannot be a field of the subscription type, which streams. Fix: return "
                + "a Publisher<T> without suspend.";
    }

    /**
     * @param found the Java type that the suspend function declares
     * @return the sentence for a Kotlin {@code suspend} function that returns a stage or a publisher
     */
    public static String suspendWithAsyncReturn(String found) {
        return "a Kotlin suspend function already returns its value later, and cannot also return " + found
                + ". Fix: declare the value type as the return type of the suspend function, or drop suspend.";
    }

    /**
     * @return the sentence for a {@code Publisher} that a bean property holds
     */
    public static String publisherOnProperty() {
        return PUBLISHER + " is only adapted from a method. Fix: return it from a resolver method, which subscribes "
                + "to it for each call, instead of exposing it as a property.";
    }

    /**
     * @return the sentence for a {@code Publisher} that is the element of a list
     */
    public static String publisherInList() {
        return PUBLISHER + " is not allowed inside a list. Fix: return Flux<T> (or Mono<List<T>>) for the list field "
                + "itself, or resolve the elements first.";
    }

    /**
     * @return the sentence for a {@code Publisher} inside a {@code CompletionStage} outside the subscription type
     */
    public static String publisherInCompletionStage() {
        return PUBLISHER + " is not allowed inside a CompletionStage on a field outside the subscription type. Fix: "
                + "return the Publisher itself (Mono<T> or Flux<T>), it is adapted for you, or return CompletionStage<T>.";
    }

    /**
     * @param graphQlType the printed GraphQL type of the field
     * @return the sentence for a {@code Publisher} of many values on a field that is not a list
     */
    public static String multiValuePublisherNeedsList(String graphQlType) {
        return PUBLISHER + " of many values is only allowed on a list field outside the subscription type, the field "
                + "type is " + graphQlType + ". Fix: return Mono<T> for a single value, or make the GraphQL field a "
                + "list.";
    }

    /**
     * @param returned    the simple name of the value-less return type, such as {@code Completable}
     * @param graphQlType the printed GraphQL type of the field
     * @return the sentence for a value-less single-value return on a non-null field
     */
    public static String valuelessAtNonNull(String returned, String graphQlType) {
        return returned + " emits no value, so the field resolves to null, and the GraphQL type is " + graphQlType
                + ". Fix: make the GraphQL field nullable (remove the trailing '!'), or return a type that carries the value.";
    }

    /**
     * @param graphQlType the printed GraphQL type of the field, or of the list element
     * @param fieldLevel  whether the type is the type of the field itself, not the element of a list
     * @return the sentence for an {@code Optional} at a non-null position
     */
    public static String optionalAtNonNull(String graphQlType, boolean fieldLevel) {
        return OPTIONAL + " is only allowed for a nullable GraphQL type, the " + typeOf(fieldLevel) + " is " + graphQlType
                + ". Fix: make the GraphQL " + typeOf(fieldLevel) + " nullable (remove the trailing '!'), or return the "
                + "value itself instead of an Optional.";
    }

    /**
     * @param primitiveOptional the binary name of {@code OptionalInt}, {@code OptionalLong} or {@code OptionalDouble}
     * @param graphQlType       the printed GraphQL type of the field, or of the list element
     * @param fieldLevel        whether the type is the type of the field itself, not the element of a list
     * @return the sentence for a primitive optional at a non-null position
     */
    public static String primitiveOptionalAtNonNull(String primitiveOptional, String graphQlType, boolean fieldLevel) {
        return primitiveOptional + " is only allowed for a nullable GraphQL type, the " + typeOf(fieldLevel) + " is "
                + graphQlType + ". Fix: make the GraphQL " + typeOf(fieldLevel) + " nullable (remove the trailing '!'), "
                + "or return the value itself instead of an " + simpleName(primitiveOptional) + ".";
    }

    /**
     * @param primitiveOptional the binary name of {@code OptionalInt}, {@code OptionalLong} or {@code OptionalDouble}
     * @return the sentence for a primitive optional inside an {@code Optional}
     */
    public static String optionalCannotWrapPrimitiveOptional(String primitiveOptional) {
        return OPTIONAL + " cannot wrap " + primitiveOptional + ". Fix: return the " + simpleName(primitiveOptional)
                + " itself, it is nullable already.";
    }

    /**
     * @param outer   the wrapper that was peeled last
     * @param wrapper the wrapper found inside it
     * @return the sentence for a wrapper inside a wrapper that cannot hold it
     */
    public static String cannotWrap(String outer, String wrapper) {
        String simple = simpleName(wrapper);
        return outer + " cannot wrap " + wrapper + ". Supported wrappers, outermost first: CompletionStage "
                + "(field level only), Publisher (field level only, never inside a CompletionStage), DataFetcherResult, Optional "
                + "(nullable types only), each at most once. Fix: remove the inner " + simple
                + " or declare the wrappers in that order.";
    }

    /**
     * @param wrapper the wrapper without a type argument
     * @return the sentence for a wrapper that does not declare the type it wraps
     */
    public static String missingTypeArgument(String wrapper) {
        String simple = simpleName(wrapper);
        return wrapper + " must declare the type it wraps. Fix: add the type argument, for example " + simple
                + "<String>.";
    }

    private static String typeOf(boolean fieldLevel) {
        return fieldLevel ? "field type" : "list element type";
    }

    private static String simpleName(String binaryName) {
        return binaryName.substring(binaryName.lastIndexOf('.') + 1);
    }
}
