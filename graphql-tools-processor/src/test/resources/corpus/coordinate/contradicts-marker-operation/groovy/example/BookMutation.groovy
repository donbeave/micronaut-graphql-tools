package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import jakarta.inject.Singleton

@Singleton
class BookMutation implements BookMutationContract {

    @Override
    @GraphQLSchemaCoordinate('Query.book')
    Book rename(String id) {
        return new Book()
    }

}
