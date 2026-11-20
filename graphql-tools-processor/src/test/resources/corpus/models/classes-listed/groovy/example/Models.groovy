package example

import io.micronaut.graphql.tools.annotation.GraphQLModels

@GraphQLModels(classes = [Book, Author])
class Models {
}
