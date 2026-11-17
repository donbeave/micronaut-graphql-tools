/*
 * Copyright 2021-2022 original authors
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
package io.micronaut.graphql.tools.exceptions;

import io.micronaut.graphql.tools.MappingContext;

/**
 * Thrown when an object type of a union or interface is mapped to a Java type that is not a subtype of the Java type
 * the field declares for the union or interface, so that no object the field returns can resolve to it.
 *
 * @since 1.0.0
 */
public final class IncorrectMemberMappingException extends AbstractMappingException {

    private final String abstractTypeName;
    private final String objectTypeName;
    private final Class<?> declaredClass;
    private final Class<?> memberClass;

    /**
     * @param mappingContext   the field that returns the union or interface
     * @param abstractTypeKind {@code union} or {@code interface}
     * @param abstractTypeName the name of the union or interface
     * @param declaredClass    the Java type the field declares for it
     * @param objectTypeName   the object type
     * @param memberClass      the Java type the object type is mapped to
     */
    public IncorrectMemberMappingException(MappingContext mappingContext, String abstractTypeKind,
                                           String abstractTypeName, Class<?> declaredClass, String objectTypeName,
                                           Class<?> memberClass) {
        super(
                String.format(
                        "The %s %s is returned as %s, but its object type %s is mapped to %s, which is not a %s, so no "
                                + "object the field returns can resolve to %s.",
                        abstractTypeKind, abstractTypeName, declaredClass.getName(), objectTypeName,
                        memberClass.getName(), declaredClass.getName(), objectTypeName
                ),
                mappingContext
        );
        this.abstractTypeName = abstractTypeName;
        this.objectTypeName = objectTypeName;
        this.declaredClass = declaredClass;
        this.memberClass = memberClass;
    }

    /**
     * @return the name of the union or interface
     */
    public String getAbstractTypeName() {
        return abstractTypeName;
    }

    /**
     * @return the object type
     */
    public String getObjectTypeName() {
        return objectTypeName;
    }

    /**
     * @return the Java type the field declares for the union or interface
     */
    public Class<?> getDeclaredClass() {
        return declaredClass;
    }

    /**
     * @return the Java type the object type is mapped to
     */
    public Class<?> getMemberClass() {
        return memberClass;
    }

}
