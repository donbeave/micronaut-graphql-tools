package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    io.reactivex.rxjava3.core.Completable done() {
        return null
    }

    io.reactivex.rxjava3.core.Completable mustReturn() {
        return null
    }

}
