package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("query Book($id: ID!) { book(id: $id) { id title pages rating author { name born } genre tags } }")
    Book book(String id);

    @GraphQLOperation(value = "query Book($id: ID!) { book(id: $id) { id title pages rating author { name born } genre tags } }", name = "Book")
    java.util.concurrent.CompletableFuture<Book> bookLater(String id);

    @GraphQLOperation(value = "query Book($id: ID!) { book(id: $id) { id title pages rating author { name born } genre tags } }", name = "Book")
    java.util.Optional<Book> bookOptional(String id);

    @GraphQLOperation(value = "query Book($id: ID!) { book(id: $id) { id title pages rating author { name born } genre tags } }", name = "Book")
    io.micronaut.graphql.tools.client.GraphQLResponse<Book> bookEnvelope(String id);

    @GraphQLOperation(value = "query Book($id: ID!) { book(id: $id) { id title pages rating author { name born } genre tags } }", name = "Book")
    java.util.Map<String, Object> bookMap(String id);

    @GraphQLOperation(value = "query Book($id: ID!) { book(id: $id) { id title pages rating author { name born } genre tags } }", name = "Book")
    Object bookDynamic(String id);

    @GraphQLOperation("query Books { books(genre: FICTION) { id title pages } }")
    java.util.List<Book> books();

    @GraphQLOperation(value = "query Books { books(genre: FICTION) { id title pages } }", name = "Books")
    Book[] bookArray();

    @GraphQLOperation("query Maybe { maybeBooks { id } }")
    java.util.List<Book> maybe();

    @GraphQLOperation("query Version { version }")
    String version();

    @GraphQLOperation("query Count { count }")
    int count();

    @GraphQLOperation("query Stamp { stamp }")
    java.time.Instant stamp();

    @GraphQLOperation("query Labels { labels }")
    java.util.List<java.util.List<String>> labels();

    @GraphQLOperation("query Scores { scores }")
    int[] scores();

    @GraphQLOperation("query Names($show: Boolean!) { names @include(if: $show) }")
    java.util.List<String> names(boolean show);

    @GraphQLOperation("query Aliased { first: book(id: \"1\") { name: title } }")
    Named aliased();

    @GraphQLOperation("query Both { book(id: \"1\") { title } version }")
    Data both();

    @GraphQLOperation(value = "query Void { version }", name = "Void")
    void nothing();

    @GraphQLOperation("subscription Added { bookAdded { id title } }")
    org.reactivestreams.Publisher<Book> added();

    @GraphQLOperation("mutation Save($title: String!) { save(title: $title) { id } }")
    Book save(String title);

}
