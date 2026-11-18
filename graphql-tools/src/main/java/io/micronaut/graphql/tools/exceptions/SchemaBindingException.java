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

import io.micronaut.graphql.tools.binding.BindingCode;

/**
 * The base of the startup failures of the binding. {@link #code()} is the registry code of the failure, the same token
 * that the message starts with as {@code [code]}, that a binding manifest {@code !} line and the {@code checks} setting use.
 * An aggregate ({@link BindingFailuresException}, {@link StrictModeException}) has a code of its own, and each of its
 * findings carries its code in its line.
 */
public abstract class SchemaBindingException extends RuntimeException {

    private final BindingCode code;

    /**
     * @param code    the code of the failure
     * @param message the text, without the {@code [code]} prefix
     */
    protected SchemaBindingException(BindingCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * @param code    the code of the failure
     * @param message the text, without the {@code [code]} prefix
     * @param cause   the first underlying failure
     */
    protected SchemaBindingException(BindingCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * @return the code of the failure
     */
    public final BindingCode code() {
        return code;
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
