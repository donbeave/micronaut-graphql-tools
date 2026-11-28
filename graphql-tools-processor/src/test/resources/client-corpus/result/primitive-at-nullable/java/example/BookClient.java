package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("query Rated($id: ID!, $show: Boolean!) { book(id: $id) { rating pages @include(if: $show) } }")
    Rated book(String id, boolean show);

}
