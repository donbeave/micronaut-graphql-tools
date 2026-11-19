package example

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver

@GraphQLTypeResolver(Item)
class ItemResolver {

    String label(Item item) {
        return 'resolved ' + item.name
    }

}
