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
package io.micronaut.graphql.tools.codegen;

import java.util.Objects;

/**
 * A finding of the generator or of the options: it names the option or the schema element that is wrong and, for a schema, its
 * location. An invalid option is never an exception; it is a diagnostic.
 *
 * @param severity   whether the finding stops the generation
 * @param code       a stable code, one of the constants of this record
 * @param message    what is wrong and how to fix it, naming the option or the schema element
 * @param optionName the name of the option the finding is about, or {@code null} when it is not about an option
 * @param origin     where in a schema file the finding is, or {@code null} when it is not about a schema file
 */
public record Diagnostic(Severity severity, String code, String message, String optionName, Origin origin) {

    /** The code of a key that is not an option. */
    public static final String UNKNOWN_OPTION = "unknown-option";

    /** The code of an option value that cannot be read: an unknown enum constant, a number or a boolean that does not parse. */
    public static final String INVALID_OPTION_VALUE = "invalid-option-value";

    /** The code of a required option that is not set, or of a part of a list that is missing. */
    public static final String MISSING_OPTION = "missing-option";

    /** The code of options that are valid alone and invalid together. */
    public static final String INVALID_OPTION_COMBINATION = "invalid-option-combination";

    /** The code of a schema file that does not exist or cannot be read. */
    public static final String SCHEMA_FILE_UNREADABLE = "schema-file-unreadable";

    /** The code of a schema file that is not valid GraphQL syntax. */
    public static final String SCHEMA_SYNTAX_ERROR = "schema-syntax-error";

    /**
     * Creates a diagnostic.
     */
    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Creates an error about an option.
     *
     * @param code       the code
     * @param optionName the name of the option
     * @param message    the message
     * @return the diagnostic
     */
    public static Diagnostic optionError(String code, String optionName, String message) {
        return new Diagnostic(Severity.ERROR, code, message, optionName, null);
    }

    /**
     * Whether this diagnostic stops the generation.
     *
     * @return {@code true} for an error
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /**
     * How serious a diagnostic is.
     */
    public enum Severity {
        /** The generation does not produce files. */
        ERROR,
        /** The generation goes on. */
        WARNING
    }
}
