package example.tck

import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.SchemaMappingDictionary

/**
 * The mappings of a TCK scenario between the types of its schema and their classes, for the classes the binding cannot find
 * by itself: the members of a union and the implementations of an interface. A scenario with such classes provides one bean,
 * activated by {@code @Requires(property = "tck.scenario", value = "<id>")}, and strict mode then finds no unused mapping.
 */
@CompileStatic
interface TckTypeMappings {
    void register(SchemaMappingDictionary dictionary)
}
