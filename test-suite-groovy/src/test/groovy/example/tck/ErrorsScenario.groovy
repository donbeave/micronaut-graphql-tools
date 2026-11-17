package example.tck

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `errors`.

@CompileStatic
@GraphQLType
class Parent {
    String getName() {
        return 'parent'
    }

    String getBroken() {
        throw new IllegalStateException('boom')
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'errors')
class ErrorsQueries {

    String ok() {
        return 'fine'
    }

    String failNullable() {
        throw new IllegalStateException('boom')
    }

    String failNonNull() {
        throw new IllegalStateException('boom')
    }

    CompletionStage<String> failFuture() {
        return CompletableFuture.<String> failedFuture(new IllegalStateException('async boom'))
    }

    Parent nested() {
        return new Parent()
    }

    Parent nestedNonNull() {
        return new Parent()
    }

    int echo(int value) {
        return value
    }
}
