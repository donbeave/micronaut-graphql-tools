package example

import io.micronaut.graphql.tools.resolver.QueryResolver

interface BookQueryContract extends QueryResolver {

    Book lookup(String id)

}
