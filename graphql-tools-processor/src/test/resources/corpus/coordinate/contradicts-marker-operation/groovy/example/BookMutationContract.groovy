package example

import io.micronaut.graphql.tools.resolver.MutationResolver

interface BookMutationContract extends MutationResolver {

    Book rename(String id)

}
