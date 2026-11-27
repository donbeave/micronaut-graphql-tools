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

    /** The code of a schema directory without a schema file that the options {@code includes} and {@code excludes} select. */
    public static final String NO_SCHEMA_FILES = "no-schema-files";

    /** The code of a type, a field, an argument, an enum value or a root operation that two places define. */
    public static final String DUPLICATE_DEFINITION = "duplicate-definition";

    /** The code of a type extension whose base definition is in no schema file. */
    public static final String EXTENSION_WITHOUT_BASE = "extension-without-base";

    /** The code of a type extension of another kind than its base definition, such as an {@code extend type} of an enum. */
    public static final String EXTENSION_KIND_MISMATCH = "extension-kind-mismatch";

    /** The code of a type that a field, an argument, a union, an interface or a root operation names and that is defined nowhere. */
    public static final String UNKNOWN_TYPE = "unknown-type";

    /** The code of a custom scalar without an entry in {@code typeMappings}. */
    public static final String UNMAPPED_SCALAR = "unmapped-scalar";

    /** The code of a type, field, argument or enum value name that is a keyword of the output language. */
    public static final String RESERVED_NAME = "reserved-name";

    /** The code of a field name that a record component or a bean property cannot have because {@code java.lang.Object} has it. */
    public static final String OBJECT_METHOD_NAME = "object-method-name";

    /** The code of two schema elements, or an element and a generated helper class, that generate the same class name. */
    public static final String NAME_COLLISION = "name-collision";

    /** The code of a field or argument whose type is a root operation type, which has no model. */
    public static final String ROOT_TYPE_REFERENCE = "root-type-reference";

    /** The code of a {@code typeMappings} entry that names no type or field of the schema. A warning. */
    public static final String UNUSED_TYPE_MAPPING = "unused-type-mapping";

    /** The code of an {@code interfaceTypes} entry that names no object type of the schema. A warning. */
    public static final String UNUSED_INTERFACE_TYPE = "unused-interface-type";

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
