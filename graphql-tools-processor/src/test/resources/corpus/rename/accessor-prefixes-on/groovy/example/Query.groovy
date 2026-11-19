package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    String getVersion() {
        return '1'
    }

    Parcel parcel() {
        return new Parcel()
    }

}
