package example.tck;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code errors}.
 */
public final class ErrorsScenario {

    private ErrorsScenario() {
    }

    @GraphQLType
    public static class Parent {
        public String getName() {
            return "parent";
        }

        public String getBroken() {
            throw new IllegalStateException("boom");
        }
    }

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "errors")
    public static class Queries {

        public String ok() {
            return "fine";
        }

        public String failNullable() {
            throw new IllegalStateException("boom");
        }

        public String failNonNull() {
            throw new IllegalStateException("boom");
        }

        public CompletionStage<String> failFuture() {
            return CompletableFuture.failedFuture(new IllegalStateException("async boom"));
        }

        public Parent nested() {
            return new Parent();
        }

        public Parent nestedNonNull() {
            return new Parent();
        }

        public int echo(int value) {
            return value;
        }
    }

}
