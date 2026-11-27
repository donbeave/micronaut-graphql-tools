package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'books')
interface BookClient {

    @GraphQLOperation('query Book($id: ID!) { book(id: $id) { title } }')
    Object book(String id)

    @GraphQLOperation('query Books($genre: Genre, $limit: Int, $ids: [ID!], $first: Int! = 10) { books(genre: $genre, limit: $limit, ids: $ids, first: $first) { title } }')
    Object books(Genre genre, int limit, List<String> ids, Integer first)

    @GraphQLOperation(value = 'query Books($genre: Genre, $limit: Int, $ids: [ID!], $first: Int! = 10) { books(genre: $genre, limit: $limit, ids: $ids, first: $first) { title } }', name = 'Books')
    Object genreByName(String genre)

    @GraphQLOperation('query Search($filter: BookFilter!) { search(filter: $filter) { title } }')
    Object search(BookFilter filter)

    @GraphQLOperation('query At($moment: DateTime) { at(moment: $moment) }')
    Object at(java.time.Instant moment)

    @GraphQLOperation(value = 'query At2($moment: DateTime) { at(moment: $moment) }', name = 'At2')
    Object atAgain(java.time.Instant moment)

    @GraphQLOperation(value = 'query At3($moment: DateTime) { at(moment: $moment) }', name = 'At3')
    Object optionalWhen(java.util.Optional<java.time.Instant> moment)

    @GraphQLOperation('mutation Save($input: BookInput!) { save(input: $input) { id } }')
    Object save(BookInput input)

}
