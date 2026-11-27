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
package io.micronaut.graphql.tools.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.internal.client.method.ClientMethod;
import io.micronaut.graphql.tools.binding.internal.client.method.ClientParameter;
import io.micronaut.graphql.tools.binding.internal.model.JParameter;
import io.micronaut.graphql.tools.binding.internal.model.TypeModel;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The compile-time adapter of the client rules: it reads the operation methods of a client interface into the type model that the
 * rules of {@code binding.internal.client} work on. It lives in this package because the element model is package-private here.
 * An instance belongs to one visitor start and caches per instance.
 */
@Internal
public final class ClientElements {

    private static final String VARIABLE = "io.micronaut.graphql.tools.client.annotation.GraphQLVariable";
    private static final String HEADER = "io.micronaut.http.annotation.Header";
    // Header is repeatable, and the metadata of an element that carries it keeps the container
    private static final String HEADERS = "io.micronaut.http.annotation.Headers";
    private static final String COOKIE = "io.micronaut.http.annotation.CookieValue";
    private static final String BINDABLE = "io.micronaut.core.bind.annotation.Bindable";
    private static final String CONTINUATION = "kotlin.coroutines.Continuation";

    private final ElementTypeModel model = new ElementTypeModel(new ModelCoverage());

    /**
     * @return the types that the methods of this adapter refer to
     */
    public TypeModel model() {
        return model;
    }

    /**
     * @param method an operation method
     * @param member the member as findings print it
     * @return the method as the rules see it
     */
    public ClientMethod method(MethodElement method, String member) {
        List<ClientParameter> parameters = new ArrayList<>();
        for (ParameterElement element : method.getParameters()) {
            if (CONTINUATION.equals(element.getType().getName())) {
                continue;
            }
            JParameter parameter = model.parameter(element);
            ClientParameter.Kind kind = parameter.annotations().has(HEADER) || parameter.annotations().has(HEADERS) ? ClientParameter.Kind.HEADER
                    : parameter.annotations().has(COOKIE) ? ClientParameter.Kind.COOKIE
                    : parameter.annotations().has(BINDABLE) ? ClientParameter.Kind.OTHER : ClientParameter.Kind.VARIABLE;
            parameters.add(new ClientParameter(parameter.name(), parameter.annotations().stringValue(VARIABLE, "value").orElse(""),
                    kind, parameter.type()));
        }
        return new ClientMethod(member, parameters);
    }
}
