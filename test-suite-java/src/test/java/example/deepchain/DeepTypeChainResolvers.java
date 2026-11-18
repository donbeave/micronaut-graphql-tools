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
package example.deepchain;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;

public class DeepTypeChainResolvers {

    @Requires(env = DeepTypeChainFactory.ENVIRONMENT)
    @GraphQLRootResolver
    public static class Query {

        public Node chainRoot() {
            Node node = new Node();
            node.setName("n");
            node.setNext(new Node());
            node.getNext().setName("n");
            return node;
        }

        public String chainEcho(In input) {
            return input.getName() + (input.getNext() == null ? "" : ">" + input.getNext().getName());
        }
    }

    @GraphQLType
    @Introspected
    public static class Node {
        private String name;
        private Node next;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Node getNext() {
            return next;
        }

        public void setNext(Node next) {
            this.next = next;
        }
    }

    @GraphQLInput
    @Introspected
    public static class In {
        private String name;
        private In next;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public In getNext() {
            return next;
        }

        public void setNext(In next) {
            this.next = next;
        }
    }

}
