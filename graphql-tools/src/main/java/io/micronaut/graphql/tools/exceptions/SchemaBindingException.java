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
package io.micronaut.graphql.tools.exceptions;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.mapping.MappingContext;
import io.micronaut.graphql.tools.binding.BindingCode;

import java.util.List;

/**
 * The failure of the binding at startup: the one exception to catch. {@link #getCode()} is the registry code of the
 * failure, the same token that the message starts with as {@code [code]}, that a binding manifest {@code !} line and
 * the {@code checks} setting use. The guide page "Exceptions" lists every code.
 *
 * <p>A failure about one schema element names it: {@link #getCoordinate()} is its schema coordinate and
 * {@link #getMappingContext()} says more. A failure that gathers several ({@link BindingCode#BINDING_FAILURES} and
 * {@link BindingCode#STRICT_FINDINGS}) lists them in {@link #getFailures()} and {@link #getCodes()}, and each of them
 * carries its own code. The library creates the exceptions; an application only catches them.</p>
 */
public abstract class SchemaBindingException extends ConfigurationException {

    private final BindingCode code;
    private final transient MappingContext mappingContext;

    /**
     * @param code    the code of the failure
     * @param message the text, without the {@code [code]} prefix
     */
    protected SchemaBindingException(BindingCode code, String message) {
        super(message);
        this.code = code;
        this.mappingContext = null;
    }

    /**
     * @param code    the code of the failure
     * @param message the text, without the {@code [code]} prefix
     * @param cause   the first underlying failure
     */
    protected SchemaBindingException(BindingCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.mappingContext = null;
    }

    /**
     * @param code           the code of the failure
     * @param message        the text, without the {@code [code]} prefix
     * @param mappingContext the schema element and Java element the failure is about
     */
    protected SchemaBindingException(BindingCode code, String message, MappingContext mappingContext) {
        super(message);
        this.code = code;
        this.mappingContext = mappingContext;
    }

    /**
     * @return the code of the failure
     */
    public final BindingCode getCode() {
        return code;
    }

    /**
     * @return the schema element and Java element the failure is about, or {@code null} if it is about no single element
     */
    @Nullable
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    /**
     * @return the schema coordinate of the element the failure is about, for example {@code Query.book}, or {@code null}
     * if it is about no single element
     */
    @Nullable
    public String getCoordinate() {
        return mappingContext == null ? null : mappingContext.getCoordinate();
    }

    /**
     * @return the failures of several fields or types, for {@link BindingCode#BINDING_FAILURES}; empty for any other
     * code
     */
    public List<SchemaBindingException> getFailures() {
        return List.of();
    }

    /**
     * @return the check code of each finding, for {@link BindingCode#STRICT_FINDINGS}; empty for any other code
     */
    public List<BindingCode> getCodes() {
        return List.of();
    }

    @Override
    public String getMessage() {
        return code.tag(describe(super.getMessage()));
    }

    /**
     * @param message the text as given to the constructor
     * @return the text to show after the code
     */
    protected String describe(String message) {
        return message;
    }
}
