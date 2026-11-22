package example

import io.micronaut.graphql.tools.resolver.QueryResolver

interface BookQueryContract : QueryResolver {

    fun book(id: String, extra: String): Book

}
