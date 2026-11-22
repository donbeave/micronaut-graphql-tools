package example;

import io.micronaut.graphql.tools.resolver.QueryResolver;

public interface RootTagContract extends QueryResolver {

    String tag();

}
