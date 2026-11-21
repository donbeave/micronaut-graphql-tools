package example

import io.micronaut.graphql.tools.resolver.MutationResolver

interface BookMutationContract : MutationResolver {

    fun rename(id: String): Book

}
