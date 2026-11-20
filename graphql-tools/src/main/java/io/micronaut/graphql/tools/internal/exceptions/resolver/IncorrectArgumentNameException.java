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
package io.micronaut.graphql.tools.internal.exceptions.resolver;

import io.micronaut.graphql.tools.internal.exceptions.AbstractMappingException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.mapping.MappingContext;

import java.util.List;

/**
 * A resolver method parameter and a GraphQL field argument cannot be matched by name.
 *
 * @author Alexey Zhokhov
 */
@Internal
public final class IncorrectArgumentNameException extends AbstractMappingException {

    private IncorrectArgumentNameException(String message, MappingContext mappingContext) {
        super(BindingCode.ARGUMENT_MISMATCH, message, mappingContext);
    }

    public static IncorrectArgumentNameException parameterWithoutArgument(MappingContext mappingContext,
                                                                         String parameterName,
                                                                         String argumentName,
                                                                         List<String> argumentNames) {
        return parameterWithoutArgument(mappingContext, parameterName, argumentName, argumentNames, false);
    }

    public static IncorrectArgumentNameException parameterWithoutArgument(MappingContext mappingContext,
                                                                         String parameterName,
                                                                         String argumentName,
                                                                         List<String> argumentNames,
                                                                         boolean syntheticName) {
        if (!parameterName.equals(argumentName)) {
            return new IncorrectArgumentNameException(
                    String.format(
                            "The method parameter '%s' is bound by @GraphQLArgument(\"%s\") to a GraphQL argument"
                                    + " that does not exist (arguments: %s). Correct the annotation value.",
                            parameterName,
                            argumentName,
                            argumentNames
                    ),
                    mappingContext
            );
        }
        if (syntheticName) {
            return new IncorrectArgumentNameException(
                    String.format(
                            "The method parameter '%s' has no GraphQL argument with that name (arguments: %s): the"
                                    + " class file has no parameter names. Compile the class that declares the method"
                                    + " with -parameters, or bind the parameter with @GraphQLArgument(\"<argument name>\").",
                            parameterName,
                            argumentNames
                    ),
                    mappingContext
            );
        }
        return new IncorrectArgumentNameException(
                String.format(
                        "The method parameter '%s' has no GraphQL argument with that name (arguments: %s). Rename the"
                                + " parameter, or bind it with @GraphQLArgument(\"<argument name>\").",
                        parameterName,
                        argumentNames
                ),
                mappingContext
        );
    }

    public static IncorrectArgumentNameException duplicateParameter(MappingContext mappingContext,
                                                                   String argumentName) {
        return new IncorrectArgumentNameException(
                String.format(
                        "More than one method parameter is bound to the GraphQL argument '%s'. Rename the parameter,"
                                + " or bind it with @GraphQLArgument(\"<argument name>\").",
                        argumentName
                ),
                mappingContext
        );
    }

    public static IncorrectArgumentNameException untypedDataLoader(MappingContext mappingContext, String parameterName) {
        return new IncorrectArgumentNameException(
                String.format(
                        "The method parameter '%s' is a DataLoader without its key and value types. Declare it as"
                                + " DataLoader<K, V>, with the key and value types of the loader bean.",
                        parameterName
                ),
                mappingContext
        );
    }

}
