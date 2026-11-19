package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Item)
class ItemResolver {

    @GraphQLSchemaCoordinate("Item.label")
    String label(Item item) {
        return 'resolved ' + item.name
    }

}
