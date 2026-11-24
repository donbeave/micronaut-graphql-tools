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
package example.tester

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLContextValue
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton

@CompileStatic
@GraphQLType
class TesterBook {

    final String id
    final String title

    TesterBook(String id, String title) {
        this.id = id
        this.title = title
    }

}

// tag::resolver[]
@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(env = TesterFixtures.TESTER)
class TesterQuery {

    TesterBook testerBook(String id) {
        id == 'none' ? null : new TesterBook(id, "Book $id".toString())
    }

    int testerPages(int count) {
        count * 2
    }

    String testerWhoAmI(@GraphQLContextValue('tester.user') String user) {
        user
    }

    String testerFailing() {
        throw new IllegalStateException('boom')
    }

}
// end::resolver[]
