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
package io.micronaut.graphql.tools.internal.input;

import graphql.language.SourceLocation;
import graphql.language.TypeDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.mapping.MappingContext;

/**
 * The mapping context of a finding about the Java class that backs an input type or an enum: the coordinate is the schema
 * type, not the argument or field that reaches it, so that it is the same whichever use the startup binds the type
 * from (the compile-time check reports the same coordinate). The message names the use that the startup reached it from.
 */
@Internal
public final class TypeDefinitionMappingContext implements MappingContext {

    private final TypeDefinition<?> typeDefinition;
    private final Class<?> mappedClass;
    private final MappingContext reachedFrom;

  public   TypeDefinitionMappingContext(TypeDefinition<?> typeDefinition, Class<?> mappedClass, MappingContext reachedFrom) {
        this.typeDefinition = typeDefinition;
        this.mappedClass = mappedClass;
        this.reachedFrom = reachedFrom;
    }

    @Override
    public Class<?> getMappedClass() {
        return mappedClass;
    }

    @Override
    public String getCoordinate() {
        return typeDefinition.getName();
    }

    @Override
    @Nullable
    public SourceLocation getSourceLocation() {
        return typeDefinition.getSourceLocation();
    }

    @Override
    public String getMessage(String superMessage) {
        return superMessage
                + "\n  GraphQL coordinate: " + MappingContext.describeCoordinate(getCoordinate(), getSourceLocation())
                + "\n  Mapped class: " + mappedClass.getName()
                + "\n  Reached from: " + MappingContext.describeCoordinate(reachedFrom.getCoordinate(),
                reachedFrom.getSourceLocation());
    }
}
