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
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.exceptions.SchemaBindingException;

/**
 * Thrown when a bean is a {@link GraphQLTypeResolver} only through an inherited annotation, and the build did not record
 * its contracts: the visitor of {@code micronaut-graphql-tools-processor} writes them into the annotation metadata of the
 * bean class, so the processor is not on the path of the compiler. The same holds for a bean that carries the annotation
 * and inherits another contract.
 */
@Internal
public final class TypeResolverContractsNotFoundException extends SchemaBindingException {

    private final Class<?> beanType;

    public TypeResolverContractsNotFoundException(Class<?> beanType) {
        this(beanType, false);
    }

    /**
     * @param annotatedDirectly whether the bean carries the annotation itself and inherits another contract as well,
     *                          so annotating the class directly does not help
     */
    public TypeResolverContractsNotFoundException(Class<?> beanType, boolean annotatedDirectly) {
        super(BindingCode.TYPE_RESOLVER_CONTRACTS_NOT_FOUND, "The bean " + beanType.getName() + " is a @"
                + GraphQLTypeResolver.class.getSimpleName() + (annotatedDirectly
                ? " that also inherits another contract, but the build did not record its contracts. "
                : " only through an inherited annotation, but the build did not record its contracts. ")
                + "Add micronaut-graphql-tools-processor to the path that your compiler reads "
                + "Micronaut processors from (annotationProcessor for Java, ksp or kapt for Kotlin, compileOnly for "
                + "Groovy; the compile-time checks section of the README shows the setting of each tool, and "
                + "micronaut.graphql.tools.processor.checks=false compiles without the checks and still records the "
                + "contracts)" + (annotatedDirectly ? "." : ", or annotate the bean class directly."));

        this.beanType = beanType;
    }

    private TypeResolverContractsNotFoundException(Class<?> beanType, String message) {
        super(BindingCode.TYPE_RESOLVER_CONTRACTS_NOT_FOUND, message);

        this.beanType = beanType;
    }

    /**
     * @param beanType a bean that implements QueryResolver, MutationResolver or SubscriptionResolver
     * @return the failure for a bean whose root operation the build did not record
     */
    public static TypeResolverContractsNotFoundException forRootMarker(Class<?> beanType) {
        return new TypeResolverContractsNotFoundException(beanType, "The bean " + beanType.getName() + " implements a "
                + "root resolver marker interface (QueryResolver, MutationResolver or SubscriptionResolver), but the build did "
                + "not record its operation. Add micronaut-graphql-tools-processor to the path that your compiler reads "
                + "Micronaut processors from (annotationProcessor for Java, ksp or kapt for Kotlin, compileOnly for "
                + "Groovy; the compile-time checks section of the README shows the setting of each tool, and "
                + "micronaut.graphql.tools.processor.checks=false compiles without the checks and still records the "
                + "operation).");
    }

    public Class<?> getBeanType() {
        return beanType;
    }

}
