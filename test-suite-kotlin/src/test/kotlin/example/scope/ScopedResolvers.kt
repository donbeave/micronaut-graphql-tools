package example.scope

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.http.scope.RequestScope
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

const val SCOPES_ENVIRONMENT = "resolver-scopes"

private val IDS = AtomicInteger()

@Singleton
@Requires(env = [SCOPES_ENVIRONMENT])
class ExtendQuery : TypeDefinitionRegistryCustomizer {
    override fun customize(registry: TypeDefinitionRegistry) {
        registry.merge(
            SchemaParser().parse("extend type Query { singletonId: Int! prototypeId: Int! requestId: Int! }"),
        )
    }
}

@Singleton
@GraphQLRootResolver
@Requires(env = [SCOPES_ENVIRONMENT])
class SingletonResolver {
    private val id = IDS.incrementAndGet()
    fun singletonId(): Int = id
}

@Prototype
@GraphQLRootResolver
@Requires(env = [SCOPES_ENVIRONMENT])
class PrototypeResolver {
    private val id = IDS.incrementAndGet()
    fun prototypeId(): Int = id
}

@RequestScope
@GraphQLRootResolver
@Requires(env = [SCOPES_ENVIRONMENT])
open class RequestResolver {
    private val id = IDS.incrementAndGet()
    open fun requestId(): Int = id
}
