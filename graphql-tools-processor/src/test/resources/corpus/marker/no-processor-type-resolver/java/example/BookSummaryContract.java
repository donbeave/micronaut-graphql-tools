package example;

import io.micronaut.graphql.tools.resolver.TypeResolver;

public interface BookSummaryContract extends TypeResolver<Book> {

    String summary(Book book);

}
