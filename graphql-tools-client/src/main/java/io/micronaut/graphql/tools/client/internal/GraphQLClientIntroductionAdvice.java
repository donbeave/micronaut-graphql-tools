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
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.Ordered;
import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.internal.serde.GraphQLCodec;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The introduction advice of the declarative client. It turns a call of a method that carries {@code @GraphQLOperation} into a
 * request, sends it with the Micronaut HTTP client and returns the response as the declared type. Any other method of the
 * interface goes on to the Micronaut HTTP client advice.
 *
 * <p>Each method gets one {@link OperationPlan} on its first call, from the metadata that the annotation processor wrote and
 * the signature of the method, both known at compile time. The plan is cached, and a call parses no GraphQL and reads no
 * annotation.</p>
 */
@Internal
@Singleton
@InterceptorBean(GraphQLClient.class)
public final class GraphQLClientIntroductionAdvice implements MethodInterceptor<Object, Object> {

    private final HttpClientRegistry<?> registry;
    private final GraphQLCodec codec;
    private final ConversionService conversionService;
    private final ConcurrentHashMap<ExecutableMethod<?, ?>, OperationPlan> plans = new ConcurrentHashMap<>();

    GraphQLClientIntroductionAdvice(HttpClientRegistry<?> registry, GraphQLCodec codec, ConversionService conversionService) {
        this.registry = registry;
        this.codec = codec;
        this.conversionService = conversionService;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        OperationPlan plan = plan(context);
        if (plan == OperationPlan.NOT_AN_OPERATION) {
            return context.proceed();
        }
        Object[] values = context.getParameterValues();
        return switch (plan.shape()) {
            case SYNC -> plan.blocking(values);
            case FUTURE -> plan.send(values);
            case PUBLISHER -> {
                Publisher<?> publisher = Publishers.fromCompletableFuture(() -> plan.send(values));
                yield InterceptedMethod.of(context, conversionService).handleResult(publisher);
            }
            case SUSPEND -> InterceptedMethod.of(context, conversionService).handleResult(plan.send(values));
        };
    }

    private OperationPlan plan(MethodInvocationContext<Object, Object> context) {
        ExecutableMethod<Object, Object> method = context.getExecutableMethod();
        OperationPlan plan = plans.get(method);
        if (plan == null) {
            plan = OperationPlan.of(context, registry, codec, conversionService);
            OperationPlan existing = plans.putIfAbsent(method, plan);
            if (existing != null) {
                plan = existing;
            }
        }
        return plan;
    }
}
