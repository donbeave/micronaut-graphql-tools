package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    fun done(): io.reactivex.rxjava3.core.Completable? = null

    fun mustReturn(): io.reactivex.rxjava3.core.Completable? = null

}
