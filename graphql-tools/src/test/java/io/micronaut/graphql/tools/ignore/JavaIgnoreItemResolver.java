package io.micronaut.graphql.tools.ignore;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@Requires(property = "spec.name", value = "IgnoreSpec")
@GraphQLTypeResolver(JavaIgnoreItem.class)
public class JavaIgnoreItemResolver {

    public String name(JavaIgnoreItem item) {
        return "resolver";
    }

}
