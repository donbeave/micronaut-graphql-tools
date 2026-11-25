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
package io.micronaut.graphql.tools.benchmarks;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.TypeResolutionEnvironment;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.MergedField;
import graphql.execution.TypeResolutionParameters;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.language.Field;
import graphql.parser.Parser;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.security.GraphQLSecurity;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.graphql.tools.execution.GraphQLTransport;
import io.micronaut.graphql.tools.internal.execution.ExecutionState;
import io.micronaut.graphql.tools.internal.protection.GetMutationGuard;
import io.micronaut.http.HttpRequest;
import io.micronaut.graphql.tools.internal.http.GraphQLHttpResponses;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Circle;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Item;
import io.micronaut.graphql.tools.benchmarks.Fixtures.PlainItem;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Rectangle;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Square;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Triangle;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.graphql.tools.scalars.StandardScalar;
import io.micronaut.graphql.tools.scalars.internal.json.JsonCoercing;
import io.micronaut.graphql.tools.scalars.internal.time.TimeScalars;

import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class BenchmarkCases implements AutoCloseable {

    public static final String PROPERTY_FETCH = "propertyFetch";
    public static final String GRAPHQL_JAVA_PROPERTY_FETCH = "graphqlJavaPropertyFetch";
    public static final String ROOT_METHOD = "rootMethod";
    public static final String TYPE_RESOLVER_METHOD = "typeResolverMethod";
    public static final String TYPE_RESOLVER_METHOD_ARGUMENT = "typeResolverMethodWithArgument";
    public static final String SCALAR_ARGUMENT = "scalarArgument";
    public static final String INJECTED_PARAMETERS = "injectedParameters";
    public static final String INJECTED_STATE_PARAMETERS = "injectedStateParameters";
    public static final String LOCAL_DATE_COERCION = "localDateCoercion";
    public static final String INSTANT_COERCION = "instantCoercion";
    public static final String JSON_COERCION = "jsonCoercion";
    public static final String ENUM_ARGUMENT = "enumArgument";
    public static final String INPUT_OBJECT_ARGUMENT = "inputObjectArgument";
    public static final String LIST_OF_INPUTS_ARGUMENT = "listOfInputsArgument";
    public static final String LIST_OF_SCALARS_ARGUMENT = "listOfScalarsArgument";
    public static final String RECORD_INPUT_ARGUMENT = "recordInputArgument";
    public static final String NESTED_INPUT_ARGUMENT = "nestedInputArgument";
    public static final String UNION_RESOLUTION = "unionResolution";
    public static final String INTERFACE_RESOLUTION = "interfaceResolution";
    /** The union resolution with the object type cached per class and no schema check: the cost the schema check adds. */
    public static final String UNION_RESOLUTION_CACHED_TYPE = "unionResolutionCachedType";
    /** The same call with a resolver that returns one object type: what the benchmark loop itself costs. */
    public static final String UNION_RESOLUTION_CONTROL = "unionResolutionControl";
    public static final String DIRECTIVE_WRAPPED_FETCHER = "directiveWrappedFetcher";
    public static final String ASYNC_BOUNDARY_DONE = "asyncBoundaryDone";
    /** The choice of the response media type from the {@code Accept} header of an HTTP request. */
    public static final String ACCEPT_NEGOTIATION = "acceptNegotiation";
    /** The operation-type check of a GET request on a parsed document, which a cache or the execution's own parse already supplied. */
    public static final String GET_MUTATION_GUARD = "getMutationGuard";
    public static final String ASYNC_BOUNDARY_OTHER_THREAD = "asyncBoundaryOtherThread";
    /** The future of an async field that is not done, without the boundary: what the other-thread case subtracts. */
    public static final String ASYNC_PENDING_SOURCE = "asyncPendingSource";
    public static final String INVOCATION_OVERHEAD = "invocationOverhead";
    public static final String INVOCATION_SLOPE = "invocationSlope";
    public static final String LOADER_OVERHEAD = "loaderOverhead";
    /** What the metrics and tracing modules add per field to a request: the invocation slope with them on, less the one with an empty instrumentation. */
    public static final String OBSERVED_INVOCATION_SLOPE = "observedInvocationSlope";
    /** An unsecured field with the security module installed: it must allocate what the same field allocates without the module. */
    public static final String SECURITY_UNSECURED_FIELD = "securityUnsecuredField";
    /** A field of {@code @Secured} that the caller may call, with the security module installed. */
    public static final String SECURITY_SECURED_FIELD_PASSES = "securitySecuredFieldPasses";
    public static final String QUERY_1K = "query1kObjects";
    public static final String QUERY_10K = "query10kObjects";

    static final String SCHEMA = """
            schema {
              query: Query
            }

            directive @guard on FIELD_DEFINITION

            enum Color { RED GREEN BLUE }

            input PointInput {
              label: String!
              x: Int!
              y: Int!
            }

            input StockInput {
              sku: String!
              quantity: Int!
              weight: Float
            }

            input LineInput {
              label: String!
              point: PointInput!
              tags: [String!]
            }

            type Query {
              ping: String
              scalar(n: Int!): String
              injected: String
              injectedState: String
              enumArg(color: Color!): String
              input(input: PointInput!): String
              inputs(inputs: [PointInput!]!): String
              tags(tags: [String!]!): String
              stock(stock: StockInput!): String
              line(line: LineInput!): String
              items(count: Int!): [Item!]!
              shape(circle: Boolean!): Shape
              figure(triangle: Boolean!): Figure
              guarded: String @guard
              secured: String
              asyncDone: String
              asyncPending: String
              captured: String
            }

            type Item {
              id: ID!
              name: String!
              price: Int!
              label: String
              labelWithArgument(n: Int!): String
            }

            union Shape = Circle | Square

            type Circle { radius: Float! }
            type Square { side: Float! }

            interface Figure { name: String! }

            type Triangle implements Figure { name: String! }
            type Rectangle implements Figure { name: String! }
            """;

    /** The sizes, in root fields, of the queries that the invocation gates compare. */
    static final int SMALL_QUERY_FIELDS = 10;
    static final int LARGE_QUERY_FIELDS = 1_000;

    /** The {@code Instrumentation} bean of the application. */
    public enum Instrumented {
        /** None: the library sets no instrumentation. */
        NONE,
        /** One that implements no hook: what any instrumentation costs graphql-java, with nothing of ours in it. */
        EMPTY,
        /** The metrics and tracing modules', with a meter registry and the OpenTelemetry SDK of Micronaut Tracing, and no field selected for a span. */
        OBSERVABILITY,
        /** The metrics and tracing modules', with every {@code Query.ping} selected for a span by the field decorator. */
        OBSERVABILITY_SELECTED
    }

    /** The field decorator bean of the application. */
    public enum Decorators {
        /** None: no {@code GraphQLFieldFetcherDecorator} bean. */
        NONE("none"),
        /** One that is offered every field and selects none. */
        UNSELECTIVE("unselective"),
        /** One that wraps every field in a fetcher that delegates. */
        WRAP_ALL("wrapAll");

        private final String property;

        Decorators(String property) {
            this.property = property;
        }
    }

    @FunctionalInterface
    public interface Op {
        Object run() throws Exception;
    }

    private final ApplicationContext context;
    private final GraphQL graphQL;
    private final GraphQLInvocation invocation;
    private final GraphQLSchema schema;
    private final Map<String, Op> ops = new LinkedHashMap<>();

    // what graphql-java hands the data fetcher for an enum literal: the value of the enum type, not its name
    private Object coercedEnum(String type, String name) {
        return ((GraphQLEnumType) schema.getType(type)).getValue(name).getValue();
    }

    public BenchmarkCases() {
        this(0);
    }

    /**
     * @param loaders how many batch loader beans no operation uses: 0, 1 or 50
     */
    public BenchmarkCases(int loaders) {
        this(loaders, Instrumented.NONE);
    }

    /**
     * @param loaders     how many batch loader beans no operation uses: 0, 1 or 50
     * @param instrumented which {@code Instrumentation} bean the application has
     */
    public BenchmarkCases(int loaders, Instrumented instrumented) {
        this(loaders, instrumented, Decorators.UNSELECTIVE);
    }

    /**
     * @param loaders      how many batch loader beans no operation uses: 0, 1 or 50
     * @param instrumented which {@code Instrumentation} bean the application has
     * @param decorators   which {@code GraphQLFieldFetcherDecorator} bean the application has
     */
    public BenchmarkCases(int loaders, Instrumented instrumented, Decorators decorators) {
        this(loaders, instrumented, decorators, false);
    }

    /**
     * @param loaders      how many batch loader beans no operation uses: 0, 1 or 50
     * @param instrumented which {@code Instrumentation} bean the application has
     * @param decorators   which {@code GraphQLFieldFetcherDecorator} bean the application has
     * @param security     whether the security module is installed: its context contributor and its decorator of {@code @Secured} fields
     */
    public BenchmarkCases(int loaders, Instrumented instrumented, Decorators decorators, boolean security) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(SCHEMA);
        // no transport: the benchmarks call the GraphQL bean, and the websocket keep-alive task would run beside them
        ApplicationContextBuilder builder = ApplicationContext.builder();
        switch (instrumented) {
            case NONE -> { }
            case EMPTY -> builder.singletons(new SimplePerformantInstrumentation());
            // the beans of the metrics and tracing modules, with no field selected for a span
            case OBSERVABILITY, OBSERVABILITY_SELECTED -> builder.singletons(new SimpleMeterRegistry());
        }
        context = builder
                .properties(Map.of("graphql.graphql-ws.enabled", false, "graphql.graphiql.enabled", false,
                        // the scalars module is on the classpath for the coercion cases below; its beans would only add to the
                        // start-up of every other case
                        "micronaut.graphql.tools.scalars.enabled", false,
                        // the invocation gates measure the calling thread, so the operation must not hop
                        "micronaut.graphql.tools.execution.executor", "none",
                        // off in every case but the ones that measure the module: it is on the classpath for them
                        "micronaut.graphql.tools.security.enabled", security,
                        "benchmarks.loaders", loaders,
                        "benchmarks.decorator", decorators.property))
                .properties(switch (instrumented) {
                    case OBSERVABILITY_SELECTED -> Map.of("micronaut.graphql.tools.tracing.fields", List.of("Query.ping"));
                    case OBSERVABILITY -> Map.<String, Object>of();
                    // Micronaut Micrometer and Micronaut Tracing are on the classpath and would create a registry and an
                    // OpenTelemetry bean, which switches the metrics and tracing modules on in the cases that must have none of it
                    default -> Map.<String, Object>of("micronaut.metrics.enabled", false, "micronaut.otel.enabled", false);
                })
                .singletons(registry)
                .start();
        graphQL = context.getBean(GraphQL.class);
        invocation = context.getBean(GraphQLInvocation.class);
        schema = graphQL.getGraphQLSchema();

        // an execution through the library's invocation hands the async fetchers the context they find their state in
        ExecutionResult captureResult = Mono.from(invocation.invoke(new GraphQLInvocationData("{ captured }", null, Map.of()), null, null)).block();
        if (captureResult == null || !captureResult.getErrors().isEmpty()) {
            throw new IllegalStateException("The query that captures the execution context failed: "
                    + (captureResult == null ? "no result" : captureResult.getErrors()));
        }

        ops.put(PROPERTY_FETCH, fetch("Item", "name", new Item("id", "name", 1), Map.of()));
        ops.put(GRAPHQL_JAVA_PROPERTY_FETCH, graphqlJavaPropertyFetch());
        ops.put(ROOT_METHOD, fetch("Query", "ping", null, Map.of()));
        ops.put(TYPE_RESOLVER_METHOD, fetch("Item", "label", new Item("id", "name", 1), Map.of()));
        ops.put(TYPE_RESOLVER_METHOD_ARGUMENT,
                fetch("Item", "labelWithArgument", new Item("id", "name", 1), Map.of("n", 3)));
        ops.put(SCALAR_ARGUMENT, fetch("Query", "scalar", null, Map.of("n", 42)));
        ops.put(INJECTED_PARAMETERS, fetch("Query", "injected", null, Map.of()));
        ops.put(INJECTED_STATE_PARAMETERS, fetch("Query", "injectedState", null, Map.of()));
        ops.put(LOCAL_DATE_COERCION, coercion(TimeScalars.coercing(StandardScalar.LOCAL_DATE, "LocalDate", null),
                "2026-12-24"));
        ops.put(INSTANT_COERCION, coercion(TimeScalars.coercing(StandardScalar.INSTANT, "Instant", null),
                "2026-12-24T10:15:30Z"));
        ops.put(JSON_COERCION, coercion(new JsonCoercing("JSON", () -> null), jsonValue()));
        ops.put(ENUM_ARGUMENT, fetch("Query", "enumArg", null, Map.of("color", coercedEnum("Color", "GREEN"))));
        ops.put(INPUT_OBJECT_ARGUMENT, fetch("Query", "input", null, Map.of("input", point("a", 1, 2))));
        ops.put(LIST_OF_INPUTS_ARGUMENT, fetch("Query", "inputs", null,
                Map.of("inputs", List.of(point("a", 1, 2), point("b", 3, 4), point("c", 5, 6), point("d", 7, 8)))));
        ops.put(LIST_OF_SCALARS_ARGUMENT, fetch("Query", "tags", null, Map.of("tags", List.of("a", "b", "c", "d"))));
        ops.put(RECORD_INPUT_ARGUMENT, fetch("Query", "stock", null,
                Map.of("stock", Map.of("sku", "a", "quantity", 3, "weight", 2.5d))));
        ops.put(NESTED_INPUT_ARGUMENT, fetch("Query", "line", null,
                Map.of("line", Map.of("label", "l", "point", point("a", 1, 2), "tags", List.of("x", "y")))));
        if (security) {
            Resolvers.capturedContext().put(GraphQLSecurity.AUTHENTICATION_KEY, Authentication.build("user", List.of("ROLE_USER")));
        }
        ops.put(SECURITY_UNSECURED_FIELD, fetch("Query", "ping", null, Map.of()));
        ops.put(SECURITY_SECURED_FIELD_PASSES, fetch("Query", "secured", null, Map.of()));
        ops.put(DIRECTIVE_WRAPPED_FETCHER, fetch("Query", "guarded", null, Map.of()));
        ops.put(ASYNC_BOUNDARY_DONE, fetch("Query", "asyncDone", null, Map.of()));
        String[] accepts = {null, "*/*", "application/json", "application/graphql-response+json",
            "application/graphql-response+json, application/json;q=0.9", "text/html, */*;q=0.1"};
        ops.put(ACCEPT_NEGOTIATION, () -> {
            Object chosen = null;
            for (String accept : accepts) {
                chosen = GraphQLHttpResponses.negotiate(accept);
            }
            return chosen;
        });
        ops.put(GET_MUTATION_GUARD, getMutationGuard());
        ops.put(ASYNC_BOUNDARY_OTHER_THREAD, pendingFetch("Query", "asyncPending"));
        Resolvers.Query resolver = new Resolvers.Query();
        ops.put(ASYNC_PENDING_SOURCE, resolver::asyncPending);
        ops.put(UNION_RESOLUTION, resolve(schema.getType("Shape"), new Circle(), new Square()));
        ops.put(INTERFACE_RESOLUTION, resolve(schema.getType("Figure"), new Triangle(), new Rectangle()));
        ops.put(UNION_RESOLUTION_CACHED_TYPE, resolve(cachedTypeResolver(), new Circle(), new Square()));
        GraphQLObjectType circle = (GraphQLObjectType) schema.getType("Circle");
        ops.put(UNION_RESOLUTION_CONTROL, resolve(env -> circle, new Circle(), new Square()));
        ops.put(invokeOp(SMALL_QUERY_FIELDS), invoke(SMALL_QUERY_FIELDS));
        ops.put(directOp(SMALL_QUERY_FIELDS), direct(SMALL_QUERY_FIELDS));
        ops.put(invokeOp(LARGE_QUERY_FIELDS), invoke(LARGE_QUERY_FIELDS));
        ops.put(directOp(LARGE_QUERY_FIELDS), direct(LARGE_QUERY_FIELDS));
        ops.put(QUERY_1K, query(1_000));
        ops.put(QUERY_10K, query(10_000));
    }

    private static Op getMutationGuard() {
        PreparsedDocumentEntry entry = new PreparsedDocumentEntry(new Parser().parseDocument("query Read { ping } mutation Write { ping }"));
        CompletableFuture<PreparsedDocumentEntry> parsed = CompletableFuture.completedFuture(entry);
        GetMutationGuard guard = new GetMutationGuard(new PreparsedDocumentProvider() {
            @Override
            public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput input,
                                                                              Function<ExecutionInput, PreparsedDocumentEntry> parse) {
                return parsed;
            }

            public PreparsedDocumentEntry getDocument(ExecutionInput input, Function<ExecutionInput, PreparsedDocumentEntry> parse) {
                return entry;
            }
        });
        Function<ExecutionInput, PreparsedDocumentEntry> parse = in -> entry;
        ExecutionInput input = ExecutionInput.newExecutionInput("query Read { ping } mutation Write { ping }").operationName("Read").build();
        new ExecutionState(PropagatedContext.empty(), GraphQLTransport.HTTP, HttpRequest.GET("/graphql"), null,
                ConvertibleValues.empty(), null, null).storeIn(input.getGraphQLContext());
        return () -> guard.getDocumentAsync(input, parse);
    }

    /** The operation that runs a query of {@code fields} root fields through the library's invocation. */
    static String invokeOp(int fields) {
        return "invoke" + fields + "Fields";
    }

    /** The operation that runs the same query on the {@code GraphQL} bean directly. */
    static String directOp(int fields) {
        return "direct" + fields + "Fields";
    }

    public Op op(String name) {
        Op op = ops.get(name);
        if (op == null) {
            throw new IllegalArgumentException("No benchmarked operation " + name + ", known: " + ops.keySet());
        }
        return op;
    }

    public Iterable<String> names() {
        return ops.keySet();
    }

    public static boolean isWholeQuery(String name) {
        return name.startsWith("query");
    }

    private static Map<String, Object> point(String label, int x, int y) {
        return Map.of("label", label, "x", x, "y", y);
    }

    private Op fetch(String type, String field, Object source, Map<String, Object> arguments) {
        GraphQLObjectType parent = schema.getObjectType(type);
        GraphQLFieldDefinition definition = parent.getFieldDefinition(field);
        DataFetcher<?> fetcher = schema.getCodeRegistry().getDataFetcher(FieldCoordinates.coordinates(type, field),
                definition);
        DataFetchingEnvironment environment = environment(parent, definition, source, arguments);
        return () -> fetcher.get(environment);
    }

    private static final int PENDING_STAGES_PER_EXECUTION = 4_096;

    // A pending stage stays registered with its execution until the execution ends, and this operation never completes it, so
    // the operation starts a new execution after a number of calls, as the requests of a server do, instead of letting one
    // execution collect every stage of the run
    private Op pendingFetch(String type, String field) {
        GraphQLObjectType parent = schema.getObjectType(type);
        GraphQLFieldDefinition definition = parent.getFieldDefinition(field);
        DataFetcher<?> fetcher = schema.getCodeRegistry().getDataFetcher(FieldCoordinates.coordinates(type, field), definition);
        DataFetchingEnvironment environment = environment(parent, definition, null, Map.of());
        return new Op() {
            private int calls;

            @Override
            public Object run() throws Exception {
                if (++calls == PENDING_STAGES_PER_EXECUTION) {
                    calls = 0;
                    ExecutionState state = ExecutionState.of(environment.getGraphQlContext());
                    new ExecutionState(state.propagatedContext(), state.getTransport(), state.getHttpRequest(), null,
                            state.getConnectionAttributes(), state.requestExecutor(), null).storeIn(environment.getGraphQlContext());
                }
                return fetcher.get(environment);
            }
        };
    }

    // What a request does with the scalar: parse the variable, then write the value that a resolver returns from it
    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonValue() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", "x");
        value.put("tags", new ArrayList<>(List.of("a", "b")));
        value.put("ratio", 2.5d);
        return value;
    }

    private static Op coercion(Coercing<?, ?> scalar, Object input) {
        Coercing<Object, Object> coercing = (Coercing<Object, Object>) scalar;
        GraphQLContext graphQLContext = GraphQLContext.getDefault();
        Locale locale = Locale.ROOT;
        return () -> coercing.serialize(coercing.parseValue(input, graphQLContext, locale), graphQLContext, locale);
    }

    private Op graphqlJavaPropertyFetch() {
        GraphQLObjectType parent = schema.getObjectType("Item");
        GraphQLFieldDefinition definition = parent.getFieldDefinition("name");
        DataFetcher<?> fetcher = PropertyDataFetcher.fetching("name");
        DataFetchingEnvironment environment = environment(parent, definition, new PlainItem("name"), Map.of());
        return () -> fetcher.get(environment);
    }

    private DataFetchingEnvironment environment(GraphQLObjectType parent, GraphQLFieldDefinition definition,
                                                Object source, Map<String, Object> arguments) {
        return DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .graphQLSchema(schema)
                .source(source)
                .arguments(arguments)
                .graphQLContext(Resolvers.capturedContext())
                .parentType(parent)
                .fieldDefinition(definition)
                .mergedField(MergedField.newMergedField(Field.newField(definition.getName()).build()).build())
                .build();
    }

    private TypeResolver cachedTypeResolver() {
        ClassValue<GraphQLObjectType> types = new ClassValue<>() {
            @Override
            protected GraphQLObjectType computeValue(Class<?> type) {
                return (GraphQLObjectType) schema.getType(type.getSimpleName());
            }
        };
        return env -> types.get(env.getObject().getClass());
    }

    private Op resolve(Object abstractType, Object first, Object second) {
        return resolve(abstractType instanceof GraphQLUnionType union
                ? schema.getCodeRegistry().getTypeResolver(union)
                : schema.getCodeRegistry().getTypeResolver((GraphQLInterfaceType) abstractType), first, second);
    }

    private Op resolve(TypeResolver resolver, Object first, Object second) {
        TypeResolutionEnvironment[] environments = {
                TypeResolutionParameters.newParameters().value(first).schema(schema).build(),
                TypeResolutionParameters.newParameters().value(second).schema(schema).build()
        };
        return new Op() {
            private int next;

            @Override
            public Object run() {
                return resolver.getType(environments[(next++) & 1]);
            }
        };
    }

    private static String fieldsQuery(int fields) {
        StringBuilder query = new StringBuilder("{");
        for (int i = 0; i < fields; i++) {
            query.append(" f").append(i).append(": ping");
        }
        return query.append(" }").toString();
    }

    // The invocation returns a Mono that does no work until it is subscribed to, so the operation subscribes and waits
    // (the way a transport does); the query runs on the calling thread (executor none). It fails when the query did not run.
    private Op invoke(int fields) {
        GraphQLInvocationData data = new GraphQLInvocationData(fieldsQuery(fields), null, Map.of());
        return () -> executed(Mono.from(invocation.invoke(data, null, null)).block());
    }

    private Op direct(int fields) {
        String query = fieldsQuery(fields);
        return () -> executed(graphQL.executeAsync(ExecutionInput.newExecutionInput().query(query).build()).join());
    }

    private static ExecutionResult executed(ExecutionResult result) {
        if (result == null || !result.getErrors().isEmpty()) {
            throw new IllegalStateException("The benchmarked query did not run: " + (result == null ? "no result" : result.getErrors()));
        }
        Map<String, Object> data = result.getData();
        if (data == null || data.isEmpty() || !"pong".equals(data.get("f0"))) {
            throw new IllegalStateException("The benchmarked query returned no data: " + data);
        }
        return result;
    }

    /**
     * A query of one root field and {@code items * 5} fields below it, run on the {@code GraphQL} bean. The operation fails when
     * the result has errors.
     */
    public Op wideQuery(int items) {
        String query = "{ items(count: " + items + ") { id name price label labelWithArgument(n: 1) } }";
        return () -> {
            ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build());
            if (!result.getErrors().isEmpty()) {
                throw new IllegalStateException(result.getErrors().toString());
            }
            return result;
        };
    }

    private Op query(int count) {
        String query = "{ items(count: " + count + ") { id name price label } }";
        return () -> {
            ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build());
            if (!result.getErrors().isEmpty()) {
                throw new IllegalStateException(result.getErrors().toString());
            }
            return result;
        };
    }

    @Override
    public void close() {
        context.close();
    }
}
