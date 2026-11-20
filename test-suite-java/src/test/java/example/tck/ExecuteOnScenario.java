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
package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLField;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The fixtures of the TCK scenario {@code execute-on}: two counting executors with threads of a known name, one that runs
 * the operations and one that only {@code @ExecuteOn} names, and fields that say which thread they run on. A hop is one
 * task handed to the executor, so the counts show which fields hopped.
 */
public final class ExecuteOnScenario {

    static final String SCENARIO = "execute-on";
    static final String REQUEST = "tck-execute-on-request";
    static final String OTHER = "tck-execute-on-other";

    private ExecuteOnScenario() {
    }

    /** An executor that counts the tasks it is given and names its threads after the pool, without a number. */
    abstract static class CountingExecutor extends ThreadPoolExecutor {

        private final AtomicInteger tasks = new AtomicInteger();

        CountingExecutor(String pool) {
            super(0, Integer.MAX_VALUE, 10, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, pool + "-pool");
                thread.setDaemon(true);
                return thread;
            });
        }

        @Override
        public void execute(Runnable command) {
            tasks.incrementAndGet();
            super.execute(command);
        }

        int tasks() {
            return tasks.get();
        }
    }

    @Singleton
    @Named(REQUEST)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class RequestExecutor extends CountingExecutor {
        public RequestExecutor() {
            super(REQUEST);
        }
    }

    @Singleton
    @Named(OTHER)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class OtherExecutor extends CountingExecutor {
        public OtherExecutor() {
            super(OTHER);
        }
    }

    @GraphQLType
    public static class ExecuteOnBox {

        public String getProperty() {
            return Thread.currentThread().getName();
        }

        @GraphQLField
        @ExecuteOn(OTHER)
        public String modelMethod() {
            return Thread.currentThread().getName();
        }
    }

    // tag::execute-on-model[]
    @GraphQLType
    @ExecuteOn(OTHER)
    public static class ExecuteOnCrate {

        public String getProperty() {
            return Thread.currentThread().getName();
        }

        @GraphQLField
        public String crated() {
            return Thread.currentThread().getName();
        }
    }
    // end::execute-on-model[]

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Queries {

        private final RequestExecutor request;
        private final OtherExecutor other;

        public Queries(RequestExecutor request, OtherExecutor other) {
            this.request = request;
            this.other = other;
        }

        // tag::execute-on-method[]
        public String onThreadPlain() {
            return Thread.currentThread().getName();
        }

        @ExecuteOn(OTHER)
        public String onThreadOther() {
            return Thread.currentThread().getName();
        }

        @ExecuteOn(OTHER)
        public CompletionStage<String> onThreadAsync() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
        // end::execute-on-method[]

        @ExecuteOn(REQUEST)
        public String onThreadSame() {
            return Thread.currentThread().getName();
        }

        public ExecuteOnBox executeOnBox() {
            return new ExecuteOnBox();
        }

        public ExecuteOnCrate executeOnCrate() {
            return new ExecuteOnCrate();
        }

        public int requestTasks() {
            return request.tasks();
        }

        public int otherTasks() {
            return other.tasks();
        }
    }

    // tag::execute-on-class[]
    @Singleton
    @GraphQLTypeResolver(ExecuteOnBox.class)
    @ExecuteOn(OTHER)
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class BoxFields {

        public String typeResolver(ExecuteOnBox box) {
            return Thread.currentThread().getName();
        }
    }
    // end::execute-on-class[]
}
