package example

import io.micronaut.graphql.tools.resolver.QueryResolver

interface BookQueryContract : QueryResolver {

    suspend fun book(id: String): Book

}
