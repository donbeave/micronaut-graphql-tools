package example.tck;

import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code return-types}.
 */
public final class ReturnTypesScenario {

    private ReturnTypesScenario() {
    }

    @GraphQLType
    public static class Greeting {
        private final String message;

        public Greeting(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    @GraphQLType
    public static class Scalars {
        public int getCount() {
            return Integer.MAX_VALUE;
        }

        public double getRatio() {
            return 0.5;
        }

        public boolean isFlag() {
            return true;
        }

        public String getId() {
            return "9007199254740993";
        }

        public Double getBig() {
            return null;
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "return-types")
    public static class Queries {

        public Greeting plain() {
            return new Greeting("hello");
        }

        public Optional<Greeting> present() {
            return Optional.of(new Greeting("present"));
        }

        public Optional<Greeting> absent() {
            return Optional.empty();
        }

        public CompletionStage<Greeting> future(String name) {
            return CompletableFuture.supplyAsync(() -> new Greeting("hello " + name));
        }

        public CompletionStage<List<Greeting>> futureList() {
            return CompletableFuture.supplyAsync(() -> List.of(new Greeting("one"), new Greeting("two")));
        }

        public CompletionStage<Optional<Greeting>> futureAbsent() {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        public DataFetcherResult<Greeting> result() {
            return DataFetcherResult.<Greeting>newResult().data(new Greeting("from a data fetcher result")).build();
        }

        public DataFetcherResult<List<Greeting>> partial(DataFetchingEnvironment environment) {
            return DataFetcherResult.<List<Greeting>>newResult()
                    .data(Arrays.asList(new Greeting("loaded"), null))
                    .error(GraphqlErrorBuilder.newError(environment).message("the second element could not be loaded").build())
                    .build();
        }

        public List<Integer> nulls() {
            return Arrays.asList(1, null, 3);
        }

        public String nothing() {
            return null;
        }

        public Scalars scalars() {
            return new Scalars();
        }
    }

}
