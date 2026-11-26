package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Writer.class)
public interface WriterContract {

    String authorExtra(Writer writer);

}
