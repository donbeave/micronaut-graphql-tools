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
package io.micronaut.graphql.tools.internal;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.Map;
import io.micronaut.graphql.tools.TypeMappingContext;

/**
 * A field of an object type that an interface also declares, and what the type phase found for it.
 *
 * @param objectTypeDefinition the object type
 * @param fieldDefinition      the field
 * @param ownSourceClass       the Java class that the object type is bound to
 * @param ownMembers           the members of that class that can bind the field
 * @param modelMember          the property or method of the model that can bind the field, if there is one
 */
@Internal
record InterfaceField(ObjectTypeDefinition objectTypeDefinition, FieldDefinition fieldDefinition,
                      Class<?> ownSourceClass, Map<Class<?>, List<String>> ownMembers,
                      @Nullable ModelMember modelMember) {

    String coordinate() {
        return objectTypeDefinition.getName() + "." + fieldDefinition.getName();
    }

    TypeMappingContext mappingContext() {
        return TypeMappingContext.forField(objectTypeDefinition, fieldDefinition.getName());
    }

}
