package example.tck

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `errors`.

@GraphQLType
class Parent {
    val name: String get() = "parent"
    val broken: String get() = throw IllegalStateException("boom")
}

@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "errors")
class ErrorsQueries {

    fun ok(): String = "fine"

    fun failNullable(): String? = throw IllegalStateException("boom")

    fun failNonNull(): String = throw IllegalStateException("boom")

    fun failFuture(): CompletionStage<String?> = CompletableFuture.failedFuture(IllegalStateException("async boom"))

    fun nested(): Parent? = Parent()

    fun nestedNonNull(): Parent = Parent()

    fun echo(value: Int): Int = value
}
