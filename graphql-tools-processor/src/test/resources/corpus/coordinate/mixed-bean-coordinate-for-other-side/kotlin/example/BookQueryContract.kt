package example

import io.micronaut.graphql.tools.resolver.QueryResolver

interface BookQueryContract : QueryResolver {

    fun lookup(id: String): Book

}
