package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    String page(Integer offset, Integer first) {
        return "first=" + first + " offset=" + offset
    }

    String search(Integer limit, String term) {
        return term + ':' + limit
    }

}
