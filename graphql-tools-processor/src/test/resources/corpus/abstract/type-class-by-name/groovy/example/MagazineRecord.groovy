package example

import io.micronaut.graphql.tools.annotation.GraphQLType

@GraphQLType(name = 'Magazine')
class MagazineRecord implements SearchResult {

    int issue = 1

}
