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
package example.tck

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `protection`: a chain of nodes that the depth and complexity limits judge, and a field
// whose stage never completes, so that only the timeout ends the operation.

@CompileStatic
@GraphQLType
class ProtectedNode {
    final String name
    final ProtectedNode next

    ProtectedNode(String name, ProtectedNode next) {
        this.name = name
        this.next = next
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'protection')
class ProtectionQueries {

    ProtectedNode protectedChain() {
        return new ProtectedNode('a', new ProtectedNode('b', new ProtectedNode('c', null)))
    }

    CompletionStage<String> protectedStalled() {
        return new CompletableFuture<String>()
    }
}
