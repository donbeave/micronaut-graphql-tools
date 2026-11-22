package example

import io.micronaut.graphql.tools.resolver.MutationResolver

interface BookMutationContract : MutationResolver {

    suspend fun book(id: String, title: String): Book

}
