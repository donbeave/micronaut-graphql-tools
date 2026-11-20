package example

import io.micronaut.graphql.tools.annotation.GraphQLModels
import example.model.Book
import example.model.Author
import example.model.Marked

@GraphQLModels(packages = ["example.model"], includedAnnotations = [Marked::class])
class ZModels
