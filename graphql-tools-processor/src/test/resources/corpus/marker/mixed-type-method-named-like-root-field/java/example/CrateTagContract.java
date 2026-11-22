package example;

import io.micronaut.graphql.tools.resolver.TypeResolver;

public interface CrateTagContract extends TypeResolver<Crate> {

    String tag(Crate crate);

}
