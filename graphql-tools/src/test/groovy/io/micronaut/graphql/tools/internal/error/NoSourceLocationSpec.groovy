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
package io.micronaut.graphql.tools.internal.error

import graphql.ExecutionResultImpl
import graphql.GraphqlErrorException
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStepInfo
import graphql.execution.MergedField
import graphql.execution.ResultPath
import graphql.language.Field
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.Scalars
import io.micronaut.graphql.tools.AbstractTest
import org.intellij.lang.annotations.Language

/** A field without a source location never adds a null entry to the {@code locations} of its error (#471). */
class NoSourceLocationSpec extends AbstractTest {

    static final String SPEC = 'NoSourceLocationSpec'

    @Language('GraphQL')
    static final String SCHEMA = """
schema { query: Query }

type Query {
  ok: String
}
"""

    private Map handle(Throwable exception) {
        def handler = applicationContext.getBean(LibraryDataFetcherExceptionHandler)
        def step = ExecutionStepInfo.newExecutionStepInfo().type(Scalars.GraphQLString).path(ResultPath.rootPath().segment('ok')).build()
        def environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .mergedField(MergedField.newMergedField(Field.newField('ok').build()).build())
                .executionStepInfo(step)
                .build()
        def parameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment).exception(exception).build()
        assert parameters.sourceLocation == null
        def errors = handler.handleException(parameters).get().errors
        ExecutionResultImpl.newExecutionResult().errors(errors).build().toSpecification()
    }

    void "an unhandled exception at a field without a location has no null entry in locations"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        Map body = handle(new IllegalStateException('secret'))

        then:
        body.errors.size() == 1
        body.errors[0].path == ['ok']
        body.errors[0].locations == []
    }

    void "a GraphQLError without a path and a location at a field without a location has no null entry in locations"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        Map body = handle(GraphqlErrorException.newErrorException().message('Not allowed').build())

        then:
        body.errors.size() == 1
        body.errors[0].message == 'Not allowed'
        body.errors[0].path == ['ok']
        body.errors[0].locations == []
    }
}
