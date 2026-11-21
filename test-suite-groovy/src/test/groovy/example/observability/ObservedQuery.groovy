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
package example.observability

import groovy.transform.CompileStatic
import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import jakarta.inject.Singleton

@CompileStatic
@Requires(env = ObservabilityFixtures.OBSERVED)
@Singleton
@GraphQLRootResolver
class ObservedQuery {

    String observedHello() {
        return 'world'
    }

    ObservedBook observedBook() {
        return new ObservedBook(title: 'T')
    }

    // tag::newspan[]
    @NewSpan('observed.work')
    String observedTraced() {
        return 'traced'
    }

    @NewSpan('observed.tagged')
    String observedTagged(@SpanTag('observed.value') String value) {
        return value
    }
    // end::newspan[]

    // tag::timed[]
    @Timed('observed.timed')
    String observedTimed() {
        return 'timed'
    }
    // end::timed[]

    @Introspected
    static class ObservedBook {
        String title
    }

}
