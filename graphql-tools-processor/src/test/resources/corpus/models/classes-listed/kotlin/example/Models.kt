package example

import io.micronaut.graphql.tools.annotation.GraphQLModels

@GraphQLModels(classes = [Book::class, Author::class])
class Models
