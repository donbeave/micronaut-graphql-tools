package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import org.dataloader.DataLoader

import java.util.concurrent.CompletionStage

@GraphQLRootResolver
class Query {

    CompletionStage<String> book(DataLoader<String, String> loader, String id) {
        return loader.load(id)
    }

}
