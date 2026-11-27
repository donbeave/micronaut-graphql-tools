package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query Book(\$id: ID!) { book(id: \$id) { title } }")
    fun book(id: String): Any?

    @GraphQLOperation("query Books(\$genre: Genre, \$limit: Int, \$ids: [ID!], \$first: Int! = 10) { books(genre: \$genre, limit: \$limit, ids: \$ids, first: \$first) { title } }")
    fun books(genre: Genre?, limit: Int, ids: List<String>?, first: Int): Any?

    @GraphQLOperation(value = "query Books(\$genre: Genre, \$limit: Int, \$ids: [ID!], \$first: Int! = 10) { books(genre: \$genre, limit: \$limit, ids: \$ids, first: \$first) { title } }", name = "Books")
    fun genreByName(genre: String): Any?

    @GraphQLOperation("query Search(\$filter: BookFilter!) { search(filter: \$filter) { title } }")
    fun search(filter: BookFilter): Any?

    @GraphQLOperation("query At(\$moment: DateTime) { at(moment: \$moment) }")
    fun at(moment: java.time.Instant?): Any?

    @GraphQLOperation(value = "query At2(\$moment: DateTime) { at(moment: \$moment) }", name = "At2")
    fun atAgain(moment: java.time.Instant?): Any?

    @GraphQLOperation(value = "query At3(\$moment: DateTime) { at(moment: \$moment) }", name = "At3")
    fun optionalWhen(moment: java.time.Instant?): Any?

    @GraphQLOperation("mutation Save(\$input: BookInput!) { save(input: \$input) { id } }")
    fun save(input: BookInput): Any?

}
