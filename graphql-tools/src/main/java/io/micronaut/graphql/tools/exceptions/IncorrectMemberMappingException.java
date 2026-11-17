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
 * Thrown when an object type of a union or interface is mapped to a Java type that is provably disjoint from the Java
 * type the field declares for the union or interface (no object can be an instance of both, because the two are
 * unrelated classes or one is a {@code final} class that does not implement the other), so that no object the field
 * returns can resolve to it.
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
                        "The %s %s is returned as %s, but its object type %s is mapped to %s, and no object can be an "
                                + "instance of both %s and %s, so no object the field returns can resolve to %s. Map "
                                + "%s to a Java type that %s can implement or extend.",
                        abstractTypeKind, abstractTypeName, declaredClass.getName(), objectTypeName,
                        memberClass.getName(), declaredClass.getName(), memberClass.getName(), objectTypeName,
                        objectTypeName, declaredClass.getName()
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
