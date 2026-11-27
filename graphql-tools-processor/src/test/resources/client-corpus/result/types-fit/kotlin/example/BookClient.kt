package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query Book(\$id: ID!) { book(id: \$id) { id title pages rating author { name born } genre tags } }")
    fun book(id: String): Book?

    @GraphQLOperation(value = "query Book(\$id: ID!) { book(id: \$id) { id title pages rating author { name born } genre tags } }", name = "Book")
    fun bookLater(id: String): java.util.concurrent.CompletableFuture<Book?>

    @GraphQLOperation(value = "query Book(\$id: ID!) { book(id: \$id) { id title pages rating author { name born } genre tags } }", name = "Book")
    fun bookOptional(id: String): java.util.Optional<Book>

    @GraphQLOperation(value = "query Book(\$id: ID!) { book(id: \$id) { id title pages rating author { name born } genre tags } }", name = "Book")
    fun bookEnvelope(id: String): io.micronaut.graphql.tools.client.GraphQLResponse<Book?>

    @GraphQLOperation(value = "query Book(\$id: ID!) { book(id: \$id) { id title pages rating author { name born } genre tags } }", name = "Book")
    fun bookMap(id: String): Map<String, Any?>?

    @GraphQLOperation(value = "query Book(\$id: ID!) { book(id: \$id) { id title pages rating author { name born } genre tags } }", name = "Book")
    fun bookDynamic(id: String): Any?

    @GraphQLOperation("query Books { books(genre: FICTION) { id title pages } }")
    fun books(): List<Book>

    @GraphQLOperation(value = "query Books { books(genre: FICTION) { id title pages } }", name = "Books")
    fun bookArray(): Array<Book>

    @GraphQLOperation("query Maybe { maybeBooks { id } }")
    fun maybe(): List<Book?>?

    @GraphQLOperation("query Version { version }")
    fun version(): String?

    @GraphQLOperation("query Count { count }")
    fun count(): Int

    @GraphQLOperation("query Stamp { stamp }")
    fun stamp(): java.time.Instant?

    @GraphQLOperation("query Labels { labels }")
    fun labels(): List<List<String>>

    @GraphQLOperation("query Aliased { first: book(id: \"1\") { name: title } }")
    fun aliased(): Named?

    @GraphQLOperation("query Both { book(id: \"1\") { title } version }")
    fun both(): Data

    @GraphQLOperation(value = "query Void { version }", name = "Void")
    fun nothing(): Unit

    @GraphQLOperation("subscription Added { bookAdded { id title } }")
    fun added(): org.reactivestreams.Publisher<Book?>

    @GraphQLOperation("mutation Save(\$title: String!) { save(title: \$title) { id } }")
    fun save(title: String): Book?

}
