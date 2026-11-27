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
package io.micronaut.graphql.tools.client.internal;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.OperationType;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.graphql.tools.client.annotation.GraphQLVariable;
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException;
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec;
import io.micronaut.graphql.tools.client.internal.serde.OperationRequest;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Everything that a call of one operation method needs, worked out once from the compile-time metadata and the signature of the
 * method: the document, how each parameter binds (a variable, a header or a cookie), what the result is and how it is returned.
 * A call only picks the values of its parameters.
 */
@Internal
final class OperationPlan {

    /**
     * The plan of a method that is not an operation, which goes on to the Micronaut HTTP client advice.
     */
    static final OperationPlan NOT_AN_OPERATION = new OperationPlan();

    private static final String CONTINUATION = "kotlin.coroutines.Continuation";
    private static final String UNIT = "kotlin.Unit";
    private static final Argument<Object> DYNAMIC = Argument.OBJECT_ARGUMENT;

    /**
     * How a method returns its result.
     */
    enum Shape {
        /** The result is the return value, so the call blocks. */
        SYNC,
        /** The result is a {@link CompletableFuture} or {@link java.util.concurrent.CompletionStage}. */
        FUTURE,
        /** The result is a {@link org.reactivestreams.Publisher} or a type that the conversion service builds from one. */
        PUBLISHER,
        /** The result is the value of a Kotlin {@code suspend} function. */
        SUSPEND
    }

    private final Shape shape;
    private final DefaultGraphQLHttpClient client;
    private final String document;
    @Nullable
    private final String operationName;
    private final OperationType operationType;
    private final HttpMethod httpMethod;
    private final boolean unwrap;
    private final Outcome outcome;
    private final Argument<?> responseType;
    private final String[] variableNames;
    private final Argument<?>[] variableTypes;
    private final int[] variableArguments;
    private final boolean identity;
    private final String[] staticHeaderNames;
    private final String[] staticHeaderValues;
    private final String[] headerNames;
    private final int[] headerArguments;
    private final String[] cookieNames;
    private final int[] cookieArguments;
    private final ConversionService conversionService;

    private OperationPlan() {
        this.shape = Shape.SYNC;
        this.client = null;
        this.document = "";
        this.operationName = null;
        this.operationType = OperationType.QUERY;
        this.httpMethod = HttpMethod.POST;
        this.unwrap = false;
        this.outcome = Outcome.RESPONSE;
        this.responseType = DYNAMIC;
        this.variableNames = new String[0];
        this.variableTypes = new Argument<?>[0];
        this.variableArguments = new int[0];
        this.identity = false;
        this.staticHeaderNames = new String[0];
        this.staticHeaderValues = new String[0];
        this.headerNames = new String[0];
        this.headerArguments = new int[0];
        this.cookieNames = new String[0];
        this.cookieArguments = new int[0];
        this.conversionService = null;
    }

    private OperationPlan(MethodInvocationContext<Object, Object> context, DefaultGraphQLHttpClient client, GraphQLCodec codec,
                          ConversionService conversionService, String document) {
        ExecutableMethod<Object, Object> method = context.getExecutableMethod();
        AnnotationMetadata metadata = context.getAnnotationMetadata();
        String member = method.getDeclaringType().getName() + "#" + method.getMethodName();
        this.client = client;
        this.conversionService = conversionService;
        this.document = document;
        this.operationName = metadata.stringValue(GraphQLOperationMetadata.class, "operationName").filter(name -> !name.isEmpty()).orElse(null);
        this.operationType = OperationType.valueOf(metadata.stringValue(GraphQLOperationMetadata.class, "operationType").orElse("query")
                .toUpperCase(java.util.Locale.ROOT));
        this.unwrap = "field".equals(metadata.stringValue(GraphQLOperationMetadata.class, "payload").orElse("data"));
        boolean get = metadata.enumValue(GraphQLOperation.class, "method", GraphQLOperation.Method.class).orElse(GraphQLOperation.Method.POST)
                == GraphQLOperation.Method.GET;
        if (get && operationType != OperationType.QUERY) {
            throw new IllegalStateException("Only a query can be sent with GET, not a " + operationType + ", in " + member);
        }
        this.httpMethod = get ? HttpMethod.GET : HttpMethod.POST;

        String[] declared = metadata.stringValues(GraphQLOperationMetadata.class, "variables");
        Argument<?>[] arguments = method.getArguments();
        int count = method.isSuspend() ? arguments.length - 1 : arguments.length;
        int[] declaredArguments = new int[declared.length];
        Arrays.fill(declaredArguments, -1);
        List<String> headers = new ArrayList<>();
        List<Integer> headerArgs = new ArrayList<>();
        List<String> cookies = new ArrayList<>();
        List<Integer> cookieArgs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Argument<?> argument = arguments[i];
            AnnotationMetadata parameter = argument.getAnnotationMetadata();
            if (parameter.hasAnnotation(Header.class)) {
                headers.add(parameter.stringValue(Header.class).filter(name -> !name.isEmpty()).or(() -> parameter.stringValue(Header.class, "name").filter(name -> !name.isEmpty())).orElseGet(() -> NameUtils.hyphenate(argument.getName())));
                headerArgs.add(i);
            } else if (parameter.hasAnnotation(CookieValue.class)) {
                cookies.add(parameter.stringValue(CookieValue.class).filter(name -> !name.isEmpty()).orElse(argument.getName()));
                cookieArgs.add(i);
            } else if (parameter.hasStereotype(Bindable.class)) {
                throw unsupported(member, "the parameter " + argument.getName() + " is bound by an HTTP annotation; an operation method takes variables, "
                        + "@Header and @CookieValue parameters only");
            } else {
                String name = parameter.stringValue(GraphQLVariable.class).filter(value -> !value.isEmpty()).orElse(argument.getName());
                int index = Arrays.asList(declared).indexOf(name);
                if (index < 0) {
                    throw unsupported(member, "the parameter " + argument.getName() + " binds the variable $" + name + ", which the operation does not declare "
                            + "(it declares " + Arrays.toString(declared) + "); name the variable with @GraphQLVariable, or mark the parameter "
                            + "@Header or @CookieValue");
                }
                declaredArguments[index] = i;
            }
        }
        int bound = (int) Arrays.stream(declaredArguments).filter(index -> index >= 0).count();
        this.variableNames = new String[bound];
        this.variableTypes = new Argument<?>[bound];
        this.variableArguments = new int[bound];
        int at = 0;
        for (int i = 0; i < declared.length; i++) {
            if (declaredArguments[i] >= 0) {
                variableNames[at] = declared[i];
                variableTypes[at] = boxed(arguments[declaredArguments[i]]);
                variableArguments[at++] = declaredArguments[i];
            }
        }
        this.identity = bound == arguments.length && isSequence(variableArguments);
        this.headerNames = headers.toArray(new String[0]);
        this.headerArguments = headerArgs.stream().mapToInt(Integer::intValue).toArray();
        this.cookieNames = cookies.toArray(new String[0]);
        this.cookieArguments = cookieArgs.stream().mapToInt(Integer::intValue).toArray();
        List<String> staticNames = new ArrayList<>();
        List<String> staticValues = new ArrayList<>();
        for (AnnotationValue<Header> header : metadata.getAnnotationValuesByType(Header.class)) {
            String name = header.stringValue("name").orElse("");
            String value = header.stringValue().orElse("");
            if (!name.isEmpty() && !value.isEmpty() && !name.equals(value)) {
                staticNames.add(name);
                staticValues.add(value);
            }
        }
        this.staticHeaderNames = staticNames.toArray(new String[0]);
        this.staticHeaderValues = staticValues.toArray(new String[0]);

        InterceptedMethod intercepted = InterceptedMethod.of(context, conversionService);
        Argument<?> result;
        switch (intercepted.resultType()) {
            case SYNCHRONOUS -> {
                this.shape = Shape.SYNC;
                result = method.getReturnType().asArgument();
            }
            case COMPLETION_STAGE -> {
                if (method.isSuspend()) {
                    this.shape = Shape.SUSPEND;
                } else if (method.getReturnType().getType().isAssignableFrom(CompletableFuture.class)) {
                    this.shape = Shape.FUTURE;
                } else {
                    throw unsupported(member, "the return type " + method.getReturnType().getType().getName() + " is a CompletionStage that is not a "
                            + "CompletableFuture; return CompletableFuture or CompletionStage");
                }
                result = intercepted.returnTypeValue();
            }
            default -> {
                this.shape = Shape.PUBLISHER;
                result = intercepted.returnTypeValue();
            }
        }
        Class<?> type = result.getType();
        Argument<?> dataType;
        if (type == GraphQLResponse.class) {
            this.outcome = Outcome.RESPONSE;
            dataType = result.getFirstTypeVariable().orElse(DYNAMIC);
        } else if (type == void.class || type == Void.class || UNIT.equals(type.getName())) {
            this.outcome = Outcome.NOTHING;
            dataType = DYNAMIC;
        } else {
            this.outcome = Outcome.DATA;
            dataType = boxed(result);
        }
        this.responseType = codec.responseType(dataType, unwrap);
    }

    static OperationPlan of(MethodInvocationContext<Object, Object> context, HttpClientRegistry<?> registry, GraphQLCodec codec,
                            ConversionService conversionService) {
        AnnotationMetadata metadata = context.getAnnotationMetadata();
        if (!metadata.hasAnnotation(GraphQLOperation.class)) {
            return NOT_AN_OPERATION;
        }
        ExecutableMethod<Object, Object> method = context.getExecutableMethod();
        String document = metadata.stringValue(GraphQLOperationMetadata.class, "document").orElse(null);
        if (document == null || document.isEmpty()) {
            throw new GraphQLClientException(GraphQLClientException.MISSING_OPERATION, method.getDeclaringType().getName() + "#" + method.getMethodName()
                    + " is annotated with @GraphQLOperation and has no compiled operation. Apply the annotation processor "
                    + "io.micronaut.graphql.tools:micronaut-graphql-tools-processor to the module that declares the client (annotationProcessor, ksp or kapt), "
                    + "and make sure that it ran on this interface.");
        }
        DefaultGraphQLHttpClient client = new DefaultGraphQLHttpClient(registry.getClient(metadata), codec, GraphQLHttpClientFactory.endpoint(metadata));
        return new OperationPlan(context, client, codec, conversionService, document);
    }

    Shape shape() {
        return shape;
    }

    <R> CompletableFuture<R> send(Object[] values) {
        return client.executeOperation(request(values), customizer(values), unwrap, outcome);
    }

    <R> R blocking(Object[] values) {
        return client.executeOperationBlocking(request(values), customizer(values), unwrap, outcome);
    }

    private OperationRequest request(Object[] values) {
        Object[] variables = values;
        if (!identity) {
            variables = new Object[variableArguments.length];
            for (int i = 0; i < variables.length; i++) {
                variables[i] = values[variableArguments[i]];
            }
        }
        return new OperationRequest(document, operationName, operationType, httpMethod, variableNames, variableTypes, variables, responseType);
    }

    @Nullable
    private Consumer<MutableHttpRequest<?>> customizer(Object[] values) {
        if (staticHeaderNames.length == 0 && headerNames.length == 0 && cookieNames.length == 0) {
            return null;
        }
        return http -> {
            for (int i = 0; i < staticHeaderNames.length; i++) {
                http.header(staticHeaderNames[i], staticHeaderValues[i]);
            }
            for (int i = 0; i < headerNames.length; i++) {
                Object value = values[headerArguments[i]];
                if (value != null) {
                    http.header(headerNames[i], text(value));
                }
            }
            for (int i = 0; i < cookieNames.length; i++) {
                Object value = values[cookieArguments[i]];
                if (value != null) {
                    http.cookie(Cookie.of(cookieNames[i], text(value)));
                }
            }
        };
    }

    private String text(Object value) {
        return value instanceof CharSequence ? value.toString() : conversionService.convert(value, String.class).orElseGet(value::toString);
    }

    private static boolean isSequence(int[] indexes) {
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] != i) {
                return false;
            }
        }
        return true;
    }

    private static Argument<?> boxed(Argument<?> argument) {
        return argument.getType().isPrimitive() ? Argument.of(ReflectionUtils.getWrapperType(argument.getType())) : argument;
    }

    private static GraphQLClientException unsupported(String member, String reason) {
        return new GraphQLClientException(GraphQLClientException.UNSUPPORTED_SIGNATURE, member + ": " + reason);
    }
}
