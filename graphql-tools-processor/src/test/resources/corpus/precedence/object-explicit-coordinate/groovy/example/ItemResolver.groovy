package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Item)
class ItemResolver {

    @GraphQLSchemaCoordinate("Item.label")
    String label(Item item) {
        return 'resolved ' + item.name
    }

}
