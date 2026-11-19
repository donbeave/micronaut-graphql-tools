package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    Parcel parcel() {
        return new Parcel()
    }

}
