package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.dataloader.DataLoader
import jakarta.inject.Singleton

@Singleton
@GraphQLRootResolver
class Query {

    String book(DataLoader loader, String id) {
        return id
    }

}
