package example;

import io.micronaut.graphql.tools.resolver.MutationResolver;

public interface BookMutationContract extends MutationResolver {

    Book book(String id, String title);

}
