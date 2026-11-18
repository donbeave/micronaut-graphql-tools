package example.severity

import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.TypeDefinitionRegistryCustomizer
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import jakarta.inject.Singleton
import spock.lang.Specification

// The Groovy suite runs on the JVM (D-38): the schema has a type that no field returns, and the severity settings
// decide whether the startup fails.
class CheckSeveritySpec extends Specification {

    static final String ENVIRONMENT = 'check-severity'
    static final String PREFIX = 'micronaut.graphql.tools.'

    private static GraphQLSchema start(Map<String, Object> properties) {
        ApplicationContext context = ApplicationContext.run(properties, ENVIRONMENT)
        try {
            context.getBean(GraphQLSchema)
        } finally {
            context.close()
        }
    }

    private static Throwable failure(Map<String, Object> properties) {
        try {
            start(properties)
            return null
        } catch (Exception e) {
            Throwable cause = e
            while (!(cause instanceof SchemaBindingException) && cause.cause != null && !cause.cause.is(cause)) {
                cause = cause.cause
            }
            return cause
        }
    }

    void 'an unreachable type fails the startup by default'() {
        expect:
        failure([:]) instanceof SchemaBindingException
        failure([:]).message.startsWith('[unreachable-type]')
    }

    void 'warn, ignore and strict false let the startup go on'() {
        expect:
        start([(PREFIX + 'checks.unreachable-type'): 'warn']) != null
        start([(PREFIX + 'checks.unreachable-type'): 'ignore']) != null
        start([(PREFIX + 'strict'): false]) != null
    }

    void 'an explicit fail wins over strict false'() {
        expect:
        failure([(PREFIX + 'strict'): false, (PREFIX + 'checks.unreachable-type'): 'fail'])
                .message.startsWith('[unreachable-type]')
    }

    void 'an allowed type and an allowed manifest line accept the finding'() {
        expect:
        start([(PREFIX + 'allow.unreachable'): ['Orphan']]) != null
        start([(PREFIX + 'allow.findings'): ['!unreachable-type Orphan']]) != null
    }

    void 'a stale allow entry, a removed key and an unknown check fail the startup'() {
        expect:
        failure([(PREFIX + 'allow.unreachable'): ['Orphan'], (PREFIX + 'allow.findings'): ['!unreachable-type Missing']]).message.startsWith('[invalid-allow-findings]')
        failure([(PREFIX + 'unreachable-types'): 'ignore']).message.startsWith('[removed-configuration-key]')
        failure([(PREFIX + 'checks.no-such-check'): 'warn']).message.startsWith('[invalid-check-setting]')
    }

    @Singleton
    @Requires(env = CheckSeveritySpec.ENVIRONMENT)
    static class Schema implements TypeDefinitionRegistryCustomizer {
        @Override
        void customize(TypeDefinitionRegistry registry) {
            registry.merge(new SchemaParser().parse('type Orphan { id: ID }\n'))
        }
    }

}
