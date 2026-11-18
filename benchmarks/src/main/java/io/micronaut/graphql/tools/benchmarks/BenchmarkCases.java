package io.micronaut.graphql.tools.benchmarks;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.TypeResolutionEnvironment;
import graphql.execution.MergedField;
import graphql.execution.TypeResolutionParameters;
import graphql.language.Field;
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
import io.micronaut.context.ApplicationContext;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Circle;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Item;
import io.micronaut.graphql.tools.benchmarks.Fixtures.PlainItem;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Rectangle;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Square;
import io.micronaut.graphql.tools.benchmarks.Fixtures.Triangle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BenchmarkCases implements AutoCloseable {

    public static final String PROPERTY_FETCH = "propertyFetch";
    public static final String GRAPHQL_JAVA_PROPERTY_FETCH = "graphqlJavaPropertyFetch";
    public static final String ROOT_METHOD = "rootMethod";
    public static final String TYPE_RESOLVER_METHOD = "typeResolverMethod";
    public static final String TYPE_RESOLVER_METHOD_ARGUMENT = "typeResolverMethodWithArgument";
    public static final String SCALAR_ARGUMENT = "scalarArgument";
    public static final String ENUM_ARGUMENT = "enumArgument";
    public static final String INPUT_OBJECT_ARGUMENT = "inputObjectArgument";
    public static final String LIST_OF_INPUTS_ARGUMENT = "listOfInputsArgument";
    public static final String LIST_OF_SCALARS_ARGUMENT = "listOfScalarsArgument";
    public static final String RECORD_INPUT_ARGUMENT = "recordInputArgument";
    public static final String NESTED_INPUT_ARGUMENT = "nestedInputArgument";
    public static final String UNION_RESOLUTION = "unionResolution";
    public static final String INTERFACE_RESOLUTION = "interfaceResolution";
    public static final String DIRECTIVE_WRAPPED_FETCHER = "directiveWrappedFetcher";
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

    @FunctionalInterface
    public interface Op {
        Object run() throws Exception;
    }

    private final ApplicationContext context;
    private final GraphQL graphQL;
    private final GraphQLSchema schema;
    private final Map<String, Op> ops = new LinkedHashMap<>();

    // what graphql-java hands the data fetcher for an enum literal: the value of the enum type, not its name
    private Object coercedEnum(String type, String name) {
        return ((GraphQLEnumType) schema.getType(type)).getValue(name).getValue();
    }

    public BenchmarkCases() {
        TypeDefinitionRegistry registry = new SchemaParser().parse(SCHEMA);
        // no transport: the benchmarks call the GraphQL bean, and the websocket keep-alive task would run beside them
        context = ApplicationContext.builder()
                .properties(Map.of("graphql.graphql-ws.enabled", false, "graphql.graphiql.enabled", false))
                .singletons(registry)
                .start();
        graphQL = context.getBean(GraphQL.class);
        schema = graphQL.getGraphQLSchema();

        ops.put(PROPERTY_FETCH, fetch("Item", "name", new Item("id", "name", 1), Map.of()));
        ops.put(GRAPHQL_JAVA_PROPERTY_FETCH, graphqlJavaPropertyFetch());
        ops.put(ROOT_METHOD, fetch("Query", "ping", null, Map.of()));
        ops.put(TYPE_RESOLVER_METHOD, fetch("Item", "label", new Item("id", "name", 1), Map.of()));
        ops.put(TYPE_RESOLVER_METHOD_ARGUMENT,
                fetch("Item", "labelWithArgument", new Item("id", "name", 1), Map.of("n", 3)));
        ops.put(SCALAR_ARGUMENT, fetch("Query", "scalar", null, Map.of("n", 42)));
        ops.put(ENUM_ARGUMENT, fetch("Query", "enumArg", null, Map.of("color", coercedEnum("Color", "GREEN"))));
        ops.put(INPUT_OBJECT_ARGUMENT, fetch("Query", "input", null, Map.of("input", point("a", 1, 2))));
        ops.put(LIST_OF_INPUTS_ARGUMENT, fetch("Query", "inputs", null,
                Map.of("inputs", List.of(point("a", 1, 2), point("b", 3, 4), point("c", 5, 6), point("d", 7, 8)))));
        ops.put(LIST_OF_SCALARS_ARGUMENT, fetch("Query", "tags", null, Map.of("tags", List.of("a", "b", "c", "d"))));
        ops.put(RECORD_INPUT_ARGUMENT, fetch("Query", "stock", null,
                Map.of("stock", Map.of("sku", "a", "quantity", 3, "weight", 2.5d))));
        ops.put(NESTED_INPUT_ARGUMENT, fetch("Query", "line", null,
                Map.of("line", Map.of("label", "l", "point", point("a", 1, 2), "tags", List.of("x", "y")))));
        ops.put(DIRECTIVE_WRAPPED_FETCHER, fetch("Query", "guarded", null, Map.of()));
        ops.put(UNION_RESOLUTION, resolve(schema.getType("Shape"), new Circle(), new Square()));
        ops.put(INTERFACE_RESOLUTION, resolve(schema.getType("Figure"), new Triangle(), new Rectangle()));
        ops.put(QUERY_1K, query(1_000));
        ops.put(QUERY_10K, query(10_000));
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
                .parentType(parent)
                .fieldDefinition(definition)
                .mergedField(MergedField.newMergedField(Field.newField(definition.getName()).build()).build())
                .build();
    }

    private Op resolve(Object abstractType, Object first, Object second) {
        TypeResolver resolver = abstractType instanceof GraphQLUnionType union
                ? schema.getCodeRegistry().getTypeResolver(union)
                : schema.getCodeRegistry().getTypeResolver((GraphQLInterfaceType) abstractType);
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
