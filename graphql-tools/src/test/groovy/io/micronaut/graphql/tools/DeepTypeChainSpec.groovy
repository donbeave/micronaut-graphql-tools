package io.micronaut.graphql.tools

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.binding.internal.schema.SchemaSources
import io.micronaut.graphql.tools.binding.internal.schema.TypeExtensions

/**
 * A schema that is a chain of thousands of types binds on the default thread stack: the walk from a field to the types
 * it returns, and from an input field to its input type, is not one recursive call per type.
 */
class DeepTypeChainSpec extends AbstractTest {

    static final String SPEC_NAME = "DeepTypeChainSpec"

    void "start with a chain of 1000 object types and 1000 input types"() {
        when:
        startContext(chainSchema(1000), SPEC_NAME, ["micronaut.graphql.tools.strict": true])
        getGraphQLBean()

        then:
        noExceptionThrown()
        applicationContext.getBean(BindingManifest).lines.any { it.contains("Node999") }

        and:
        executeQuery("{ root { name next { name } } }").data == [root: [name: "n", next: [name: "n"]]]
        executeQuery('{ echo(input: {name: "a", next: {name: "b"}}) }').data == [echo: "a>b"]
    }

    // graphql-java builds the executable schema recursively too, and overflows the default stack with a chain this
    // deep, so the binding of the longer chain is run without it
    void "bind a chain of 5000 object types and 5000 input types"() {
        given:
        int types = 5000
        startContext(chainSchema(types), SPEC_NAME, ["micronaut.graphql.tools.strict": true])
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry().merge(new SchemaParser().parse(chainSchema(types)))
        SchemaSources.addImplicitRoots(registry)
        def generator = new GraphQLRuntimeWiringGenerator(applicationContext, new GraphQLBeanIntrospectionRegistry(),
                applicationContext.getBean(GraphQLResolversRegistry), TypeExtensions.merge(registry),
                new SchemaMappingDictionary(), applicationContext.getBean(GraphQLToolsConfiguration),
                TypeExtensions.objectExtensionFields(registry))

        when:
        generator.generate([])
        def manifest = generator.manifestRecorder.build(registry)

        then:
        noExceptionThrown()
        manifest.lines.any { it.contains("Node${types - 1}") }
    }

    private static String chainSchema(int types) {
        StringBuilder sdl = new StringBuilder("schema { query: Query }\ntype Query { root: Node0 echo(input: In0!): String! }\n")
        for (int i = 0; i < types; i++) {
            String next = i + 1 < types ? "Node${i + 1}" : "Node0"
            String nextInput = i + 1 < types ? "In${i + 1}" : "In0"
            sdl << "type Node$i { name: String! next: $next }\n"
            sdl << "input In$i { name: String! next: $nextInput }\n"
        }
        sdl.toString()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Node root() {
            new Node(name: "n", next: new Node(name: "n"))
        }

        String echo(In input) {
            input.name + (input.next == null ? "" : ">" + input.next.name)
        }
    }

    @GraphQLType
    static class Node {
        String name
        Node next
    }

    @GraphQLInput
    static class In {
        String name
        In next
    }

}
