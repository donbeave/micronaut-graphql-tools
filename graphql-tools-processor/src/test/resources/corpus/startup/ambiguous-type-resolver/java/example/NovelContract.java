package example;

import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;

@GraphQLTypeResolver(Novel.class)
public interface NovelContract {

    String bookExtra(Novel novel);

}
