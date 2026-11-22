package example;

import io.micronaut.graphql.tools.resolver.QueryResolver;

public interface CrateQueryContract extends QueryResolver {

    Crate crate();

}
