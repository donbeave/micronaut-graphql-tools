package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    String getVersion() {
        return '1'
    }

    Parcel parcel() {
        return new Parcel()
    }

}
