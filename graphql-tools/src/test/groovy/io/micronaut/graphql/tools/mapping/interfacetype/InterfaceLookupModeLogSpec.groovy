/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.mapping.interfacetype

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.internal.wiring.GraphQLRuntimeWiringGenerator
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

/**
 * The startup logs one INFO line for each object type mapped to a Java interface, and names the lookup mode it uses.
 */
class InterfaceLookupModeLogSpec extends AbstractTest {

    static final String SPEC_NAME = "InterfaceLookupModeLogSpec"

    static final Map NOT_STRICT = ['micronaut.graphql.tools.strict': false]

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  note: Note
  card: Card
}

type Note {
  text: String
}

type Card {
  title: String
}
"""

    ListAppender<ILoggingEvent> appender

    void setup() {
        appender = new ListAppender<>()
        appender.start()
        generatorLogger().addAppender(appender)
    }

    void cleanup() {
        generatorLogger().detachAppender(appender)
    }

    void "each interface-mapped type logs the lookup mode of its interface once, at startup"() {
        given:
        startContext(SCHEMA, SPEC_NAME, NOT_STRICT)

        when:
        getGraphQLBean()

        then:
        modeLines() == [
                "The type Card is mapped to the interface ${IntrospectedCard.name}, which has its own bean introspection: "
                        + "its properties are the getters of the interface",
                "The type Note is mapped to the interface ${PlainNote.name}, which has no bean introspection of its own: "
                        + "its properties are all the getters of the implementation ${PlainNoteImpl.name}"
        ].sort()
    }

    void "a request logs no lookup mode"() {
        given:
        startContext(SCHEMA, SPEC_NAME, NOT_STRICT)
        getGraphQLBean()
        appender.list.clear()

        when:
        def result = executeQuery('{ note { text } card { title } }')

        then:
        result.data == [note: [text: 'written'], card: [title: 'card']]
        modeLines().isEmpty()
    }

    private List<String> modeLines() {
        return appender.list
                .findAll { it.level.toString() == 'INFO' }
                .collect { it.formattedMessage }
                .findAll { it.contains(' is mapped to the interface ') }
                .sort()
    }

    private static Logger generatorLogger() {
        return (Logger) LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        PlainNote note() {
            return new PlainNoteImpl(text: 'written')
        }

        IntrospectedCard card() {
            return new IntrospectedCardImpl(title: 'card')
        }
    }

    static interface PlainNote {
    }

    @GraphQLType(PlainNote)
    static class PlainNoteImpl implements PlainNote {
        String text
    }

    @Introspected
    static interface IntrospectedCard {
        String getTitle()
    }

    @GraphQLType(IntrospectedCard)
    static class IntrospectedCardImpl implements IntrospectedCard {
        String title
    }
}
