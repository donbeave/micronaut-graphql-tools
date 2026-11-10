package io.micronaut.graphql.tools

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.inject.ExecutableMethod
import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 * The binding manifest renders a member from Micronaut's compile-time metadata only when reflection is not available
 * (a {@link LinkageError} or {@link SecurityException}, as in a native image without reflection configuration), and
 * says so once in the log. Any other failure is a bug and is not swallowed.
 */
class JavaTypeNamesFallbackSpec extends Specification {

    ListAppender<ILoggingEvent> appender = new ListAppender<>()
    Logger logger = (Logger) LoggerFactory.getLogger(JavaTypeNames)

    void setup() {
        JavaTypeNames.FALLBACK_LOGGED.set(false)
        appender.start()
        logger.addAppender(appender)
    }

    void cleanup() {
        logger.detachAppender(appender)
        JavaTypeNames.FALLBACK_LOGGED.set(false)
    }

    void "without reflection the compile-time metadata is used, and the first such member is logged once"() {
        given:
        ExecutableMethod first = Stub(ExecutableMethod) {
            getTargetMethod() >> { throw new NoSuchMethodError('first') }
        }
        ExecutableMethod second = Stub(ExecutableMethod) {
            getTargetMethod() >> { throw new SecurityException('second') }
        }

        when:
        def firstResult = JavaTypeNames.reflect(first)
        def secondResult = JavaTypeNames.reflect(second)

        then:
        firstResult.isEmpty()
        secondResult.isEmpty()
        appender.list.size() == 1
        appender.list[0].level == Level.INFO
        appender.list[0].formattedMessage.startsWith("The binding manifest renders Java types from Micronaut's "
                + 'compile-time metadata, without wildcards, raw types and generic array components, because '
                + 'reflection is not available (first for ')
        appender.list[0].formattedMessage.contains('java.lang.NoSuchMethodError: first')
    }

    void "a failure other than missing reflection is not swallowed"() {
        given:
        ExecutableMethod method = Stub(ExecutableMethod) {
            getTargetMethod() >> { throw new IllegalStateException('a bug') }
        }

        when:
        JavaTypeNames.reflect(method)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'a bug'
        appender.list.isEmpty()
    }

}
