package example;

import io.micronaut.graphql.tools.resolver.QueryResolver;

public interface BookQueryContract extends QueryResolver {

    Book lookup(String id);

}
