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
package io.micronaut.graphql.tools.upload.internal;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.upload.GraphQLUpload;
import io.micronaut.http.multipart.CompletedPart;

import java.util.Locale;
import java.util.Map;

/**
 * The coercion of the {@code Upload} scalar. A variable holds the part that the multipart route put there, and the part is the
 * Java value. A file has no literal and no output form: a document that writes an upload inline, and a field that returns one,
 * are errors.
 */
@Internal
final class UploadCoercing implements Coercing<CompletedPart, Object> {

    private static final String NAME = GraphQLUpload.SCALAR_NAME;

    @Override
    public Object serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale) {
        throw new CoercingSerializeException(NAME + " is an input type: a field cannot return it");
    }

    @Override
    @SuppressWarnings("deprecation")
    public Object serialize(Object dataFetcherResult) {
        return serialize(dataFetcherResult, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Override
    public CompletedPart parseValue(Object input, GraphQLContext graphQLContext, Locale locale) {
        if (input instanceof CompletedPart part) {
            return part;
        }
        throw new CoercingParseValueException(NAME + ": expected a file of a multipart request, but the variable holds "
                + (input == null ? "null" : "a value that is not a file")
                + ". Send the file as a part and name its variable in the 'map' part");
    }

    @Override
    @SuppressWarnings("deprecation")
    public CompletedPart parseValue(Object input) {
        return parseValue(input, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Override
    public CompletedPart parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) {
        throw new CoercingParseLiteralException(NAME + " cannot be written in the query: pass the file in a variable");
    }

    @Override
    @SuppressWarnings("deprecation")
    public CompletedPart parseLiteral(Object input) {
        throw new CoercingParseLiteralException(NAME + " cannot be written in the query: pass the file in a variable");
    }

    @Override
    @SuppressWarnings("deprecation")
    public CompletedPart parseLiteral(Object input, Map<String, Object> variables) {
        return parseLiteral(input);
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        throw new CoercingParseValueException(NAME + " has no literal form");
    }

    @Override
    @SuppressWarnings({"rawtypes", "deprecation"})
    public Value valueToLiteral(Object input) {
        return valueToLiteral(input, GraphQLContext.getDefault(), Locale.getDefault());
    }

}
