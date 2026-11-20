package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@Singleton
@GraphQLTypeResolver(Item)
class ItemResolver {

    String label(Item item) {
        return 'resolved ' + item.name
    }

}
