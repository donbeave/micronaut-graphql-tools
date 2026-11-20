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
package io.micronaut.graphql.tools.visitor;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.binding.BindingCode;
import io.micronaut.graphql.tools.binding.internal.finding.Finding;
import io.micronaut.graphql.tools.binding.internal.finding.Severity;
import io.micronaut.graphql.tools.binding.internal.rules.TypeClassDerivation;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The options of the visitor, read from the options of the compilation: {@code -A} under javac and kapt, {@code arg}
 * under KSP, system properties under the Groovy compiler. There are five, all under {@code micronaut.graphql.tools.processor.},
 * a segment that no runtime key uses, so that the visitor rejects an unknown key there without failing on a runtime key that
 * happens to be a system property.
 *
 * @param schemaDirs the directories to read the schema from, empty when the option is absent
 * @param moduleDir  the directory of the module that sets {@code schema.dirs}, null when the option is absent; the output of
 *                   the compilation must lie inside it, or the options are those of an earlier compilation of the same JVM
 * @param checks     whether the checks run
 * @param accessorPrefixes whether a resolver method {@code getX} or {@code isX} also binds the field {@code x}, as
 *                   {@code micronaut.graphql.tools.resolver-accessor-prefixes} says at startup
 * @param looseNullability what the checks do with a Kotlin type that is stricter than the schema type
 *                   ({@code kotlin-nullability-loose}): {@code ignore} by default
 * @param typeClassConvention the value of {@code micronaut.graphql.tools.type-class-convention} that the checks apply when
 *                   they look for two classes that match one object type, or null for none
 * @param errors     the options that are wrong; the checks do not run when there are any
 */
record ProcessorOptions(List<Path> schemaDirs, @Nullable Path moduleDir, boolean checks,
                        boolean accessorPrefixes, Severity looseNullability, @Nullable String typeClassConvention,
                        List<Finding> errors) {

    static final String PREFIX = "micronaut.graphql.tools.processor.";

    static final String SCHEMA_DIRS = PREFIX + "schema.dirs";

    static final String CHECKS = PREFIX + "checks";

    static final String MODULE_DIR = PREFIX + "module.dir";

    static final String ACCESSOR_PREFIXES = PREFIX + "resolverAccessorPrefixes";

    static final String LOOSE_NULLABILITY = PREFIX + "looseNullability";

    static final String TYPE_CLASS_CONVENTION = PREFIX + "typeClassConvention";

    static final Set<String> NAMES = Set.of(SCHEMA_DIRS, MODULE_DIR, CHECKS, ACCESSOR_PREFIXES, LOOSE_NULLABILITY,
            TYPE_CLASS_CONVENTION);

    /** The key that an earlier version of the processor read; it is an error now and names {@link #SCHEMA_DIRS}. */
    static final String REMOVED_SDL_DIR = "micronaut.graphql.tools.sdlDir";

    /**
     * The keys the visitor tells the compiler it knows: the five options and the retired key. A build that keeps the retired key
     * with {@code checks=false} must not get javac's "no processor claimed the option" warning, which {@code -Werror} turns into an error.
     * The retired key is not an option: {@link #NAMES} stays the options.
     */
    static final Set<String> ADVERTISED = Set.of(SCHEMA_DIRS, MODULE_DIR, CHECKS, ACCESSOR_PREFIXES, LOOSE_NULLABILITY,
            TYPE_CLASS_CONVENTION, REMOVED_SDL_DIR);

    ProcessorOptions {
        schemaDirs = List.copyOf(schemaDirs);
        errors = List.copyOf(errors);
    }

    static ProcessorOptions read(Map<String, String> options) {
        List<Finding> errors = new ArrayList<>();
        options.keySet().stream().sorted().forEach(key -> {
            if (key.startsWith(PREFIX) && !NAMES.contains(key)) {
                errors.add(error(BindingCode.PROCESSOR_OPTION_UNKNOWN, "the option " + key + " is not known; the options are "
                        + new TreeSet<>(NAMES)));
            }
        });

        boolean checks = flag(options, CHECKS, true, errors);
        boolean accessorPrefixes = flag(options, ACCESSOR_PREFIXES, false, errors);
        Severity looseNullability = Severity.IGNORE;
        String looseValue = options.get(LOOSE_NULLABILITY);
        if (looseValue != null) {
            Severity parsed = Severity.fromToken(looseValue);
            if (parsed == null) {
                errors.add(error(BindingCode.PROCESSOR_OPTION_INVALID, LOOSE_NULLABILITY + " must be fail, warn or ignore, not '"
                        + looseValue + "'"));
            } else {
                looseNullability = parsed;
            }
        }
        // with the checks off nothing reads a schema directory, so a leftover retired key does no harm
        if (checks && options.containsKey(REMOVED_SDL_DIR)) {
            errors.add(error(BindingCode.PROCESSOR_OPTION_REMOVED, REMOVED_SDL_DIR + " was renamed to " + SCHEMA_DIRS
                    + ": pass the schema directories there (several are joined with the path separator)"));
        }

        List<Path> directories = new ArrayList<>();
        String dirsValue = options.get(SCHEMA_DIRS);
        if (dirsValue != null) {
            for (String entry : dirsValue.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    try {
                        directories.add(Path.of(entry.trim()));
                    } catch (InvalidPathException e) {
                        errors.add(error(BindingCode.PROCESSOR_OPTION_INVALID, SCHEMA_DIRS + " holds a path that this "
                                + "system cannot use: '" + entry.trim() + "' (" + e.getReason() + ")"));
                    }
                }
            }
        }
        Path moduleDir = null;
        String moduleValue = options.get(MODULE_DIR);
        if (moduleValue != null) {
            try {
                moduleDir = Path.of(moduleValue.trim()).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                errors.add(error(BindingCode.PROCESSOR_OPTION_INVALID, MODULE_DIR + " is not a path that this system can use: '"
                        + moduleValue.trim() + "' (" + e.getReason() + ")"));
            }
        }
        String convention = options.get(TYPE_CLASS_CONVENTION);
        if (convention != null && convention.isBlank()) {
            convention = null;
        }
        String conventionProblem = TypeClassDerivation.validate(convention);
        if (conventionProblem != null) {
            errors.add(error(BindingCode.PROCESSOR_OPTION_INVALID, TYPE_CLASS_CONVENTION + ": " + conventionProblem));
        }
        return new ProcessorOptions(directories, moduleDir, checks, accessorPrefixes,
                looseNullability, convention == null ? null : convention.strip(), errors);
    }

    private static boolean flag(Map<String, String> options, String name, boolean absent, List<Finding> errors) {
        String value = options.get(name);
        if (value == null) {
            return absent;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (Set.of("false", "no", "off").contains(normalized)) {
            return false;
        }
        if (!Set.of("true", "yes", "on").contains(normalized)) {
            errors.add(error(BindingCode.PROCESSOR_OPTION_INVALID, name + " must be true or false (or yes/no, "
                    + "on/off), not '" + value + "'"));
        }
        return normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static Finding error(BindingCode code, String sentence) {
        return new Finding(code.code(), null, sentence, null, null, null, null);
    }
}
