package example;

import io.micronaut.graphql.tools.resolver.QueryResolver;

public interface BookQueryContract extends QueryResolver {

    Book book(String id, String extra);

}
