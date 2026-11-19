package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class Book {

    public String title;
    public Genre genre;

}
