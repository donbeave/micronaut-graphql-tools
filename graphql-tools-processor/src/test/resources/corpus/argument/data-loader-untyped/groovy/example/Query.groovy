package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.dataloader.DataLoader

@GraphQLRootResolver
class Query {

    String book(DataLoader loader, String id) {
        return id
    }

}
