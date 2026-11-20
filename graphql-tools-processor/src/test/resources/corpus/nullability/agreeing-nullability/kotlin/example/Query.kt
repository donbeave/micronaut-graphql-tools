package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

import java.util.Optional
import java.util.concurrent.CompletableFuture

@Singleton
@GraphQLRootResolver
class Query {

    fun title(): String = "title"

    fun subtitle(): String? = null

    fun maybe(): Optional<String> = Optional.empty()

    fun later(): CompletableFuture<String> = CompletableFuture.completedFuture("later")

    fun tags(): List<String>? = null

    fun stricter(term: String?): String = term ?: ""

    fun search(term: String, limit: Int?, note: String?): String? = term

}
