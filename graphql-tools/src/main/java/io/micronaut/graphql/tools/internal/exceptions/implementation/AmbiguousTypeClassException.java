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
package io.micronaut.graphql.tools.internal.exceptions.implementation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.graphql.tools.mapping.MappingContext;

import java.util.List;

/** Several classes match one object type of a union or interface by {@code @GraphQLType(name)} or the naming convention. */
@Internal
public final class AmbiguousTypeClassException extends AbstractMappingException {

    private final String typeName;
    private final transient List<String> classes;

    public AmbiguousTypeClassException(MappingContext mappingContext, String typeName, List<String> classes) {
        super(BindingCode.AMBIGUOUS_TYPE_CLASS, "The object type " + typeName + " matches several classes: "
                + String.join(", ", classes) + ".\nDeclare @GraphQLType(name = \"" + typeName + "\") on one class only, "
                + "rename the others, or map " + typeName + " in the SchemaMappingDictionary, which always wins.",
                mappingContext);
        this.typeName = typeName;
        this.classes = List.copyOf(classes);
    }

    /**
     * @return the object type that matches several classes
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * @return the binary names of the classes that match it
     */
    public List<String> getClasses() {
        return classes;
    }

}
