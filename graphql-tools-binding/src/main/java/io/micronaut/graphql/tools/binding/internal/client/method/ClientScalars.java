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
package io.micronaut.graphql.tools.binding.internal.client.method;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.DocumentLocation;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;

import java.util.HashMap;
import java.util.Map;

/**
 * The Java type that each custom scalar is mapped to by one client. A scalar that the catalog does not know has no wiring on
 * the client side: the Java type that a parameter or a property declares for it is the mapping, and the serializer of that type
 * does the conversion. A client that maps one scalar to two types reads the same value two ways, so the second use is a
 * finding. An instance belongs to one client interface.
 */
@Internal
public final class ClientScalars {

    private final Map<String, Mapping> mapped = new HashMap<>();

    /**
     * @param scalar   the name of a scalar that the catalog does not know
     * @param javaType the binary name of the Java type at this position, boxed
     * @param member   the method that uses it
     * @param position what uses it, for the message
     * @param location where the document has the position, null for none
     * @return the finding when the scalar is already mapped to another type, null otherwise
     */
    public @Nullable Finding map(String scalar, String javaType, String member, String position, @Nullable DocumentLocation location) {
        Mapping first = mapped.putIfAbsent(scalar, new Mapping(javaType, member, position));
        if (first == null || first.javaType.equals(javaType)) {
            return null;
        }
        return new Finding(BindingCode.CLIENT_SCALAR_CONFLICT.getCode(), member, "the scalar " + scalar + " is read as " + javaType
                + " by " + position + ", and as " + first.javaType + " by " + first.position + " of " + first.member
                + ": one client reads a scalar as one Java type", null, null, null, "use one Java type for the scalar in the "
                + "client interface", location);
    }

    private record Mapping(String javaType, String member, String position) {
    }
}
