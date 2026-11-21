package example

import io.micronaut.graphql.tools.resolver.MutationResolver

interface BookMutationContract : MutationResolver {

    fun book(id: String, title: String): Book

}
