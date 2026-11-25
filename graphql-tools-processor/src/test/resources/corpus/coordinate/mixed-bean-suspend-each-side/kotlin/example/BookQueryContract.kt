package example

import io.micronaut.graphql.tools.resolver.QueryResolver

interface BookQueryContract : QueryResolver {

    suspend fun lookup(id: String): Book

}
