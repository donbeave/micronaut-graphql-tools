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

import io.micronaut.core.annotation.Internal;
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
 * under KSP, system properties under the Groovy compiler. There are eight, all under {@code micronaut.graphql.tools.processor.},
 * a segment that no runtime key uses, so that the visitor rejects an unknown key there without failing on a runtime key that
 * happens to be a system property. The two that start with {@code client.} belong to the client visitor, which reads this record
 * too, so that one place validates every option and reports each error once.
 *
 * <p>Internal: the two visitors of this module share it; it is not an API.</p>
 *
 * @param schemaDirs the directories to read the schema from, empty when the option is absent
 * @param moduleDir  the directory of the module that sets {@code schema.dirs}, null when the option is absent (the visitor then
 *                   uses the project directory that the compiler reports, when there is one); the output of
 *                   the compilation must lie inside it, or the options are those of an earlier compilation of the same JVM
 * @param checks     whether the checks run
 * @param accessorPrefixes whether a resolver method {@code getX} or {@code isX} also binds the field {@code x}, as
 *                   {@code micronaut.graphql.tools.resolver-accessor-prefixes} says at startup
 * @param looseNullability what the checks do with a Kotlin type that is stricter than the schema type
 *                   ({@code kotlin-nullability-loose}): {@code ignore} by default
 * @param typeClassConvention the value of {@code micronaut.graphql.tools.type-class-convention} that the checks apply when
 *                   they look for two classes that match one object type, or null for none
 * @param clientDirs the directories of the client schemas, empty when the option is absent: each subdirectory is one schema
 * @param clientChecks whether the client operations are validated against their schema ({@code client.checks}); the client
 *                   visitor also stays off when {@code checks} is false
 * @param errors     the options that are wrong; the checks do not run when there are any
 */
@Internal
public record ProcessorOptions(List<Path> schemaDirs, @Nullable Path moduleDir, boolean checks,
                               boolean accessorPrefixes, Severity looseNullability, @Nullable String typeClassConvention,
                               List<Path> clientDirs, boolean clientChecks, List<Finding> errors) {

    /** The prefix of every option of the visitors. */
    public static final String PREFIX = "micronaut.graphql.tools.processor.";

    /** The schema directories of the resolvers, and the schema of the client schema named {@code server}. */
    public static final String SCHEMA_DIRS = PREFIX + "schema.dirs";

    /** Whether the checks run at all. */
    public static final String CHECKS = PREFIX + "checks";

    /** The directory of the module that sets {@link #SCHEMA_DIRS}. */
    public static final String MODULE_DIR = PREFIX + "module.dir";

    static final String ACCESSOR_PREFIXES = PREFIX + "resolver.accessor.prefixes";

    static final String LOOSE_NULLABILITY = PREFIX + "loose.nullability";

    static final String TYPE_CLASS_CONVENTION = PREFIX + "type.class.convention";

    /** The directories of the client schemas. */
    public static final String CLIENT_DIRS = PREFIX + "client.dirs";

    /** Whether the client operations are validated. */
    public static final String CLIENT_CHECKS = PREFIX + "client.checks";

    static final Set<String> NAMES = Set.of(SCHEMA_DIRS, MODULE_DIR, CHECKS, ACCESSOR_PREFIXES, LOOSE_NULLABILITY,
            TYPE_CLASS_CONVENTION, CLIENT_DIRS, CLIENT_CHECKS);

    static final String RETIRED_ACCESSOR_PREFIXES = PREFIX + "resolverAccessorPrefixes";

    static final String RETIRED_LOOSE_NULLABILITY = PREFIX + "looseNullability";

    static final String RETIRED_TYPE_CLASS_CONVENTION = PREFIX + "typeClassConvention";

    /** The key that an earlier version of the processor read; it is an error now and names {@link #SCHEMA_DIRS}. */
    static final String REMOVED_SDL_DIR = "micronaut.graphql.tools.sdlDir";

    /**
     * The retired option names and the names that replace them. A build that passes one with the checks on fails with the
     * rename; {@code -A} accepts dot-separated lowercase segments only, as every Micronaut processing option does.
     */
    static final Map<String, String> RETIRED = Map.of(
            REMOVED_SDL_DIR, SCHEMA_DIRS,
            RETIRED_ACCESSOR_PREFIXES, ACCESSOR_PREFIXES,
            RETIRED_LOOSE_NULLABILITY, LOOSE_NULLABILITY,
            RETIRED_TYPE_CLASS_CONVENTION, TYPE_CLASS_CONVENTION);

    /**
     * The keys the visitor tells the compiler it knows: the options and the retired names. A build that keeps a retired name
     * with {@code checks=false} must not get javac's "no processor claimed the option" warning, which {@code -Werror} turns into an error.
     * The retired names are not options: {@link #NAMES} stays the options.
     */
    static final Set<String> ADVERTISED = Set.of(SCHEMA_DIRS, MODULE_DIR, CHECKS, ACCESSOR_PREFIXES, LOOSE_NULLABILITY,
            TYPE_CLASS_CONVENTION, CLIENT_DIRS, CLIENT_CHECKS, REMOVED_SDL_DIR, RETIRED_ACCESSOR_PREFIXES, RETIRED_LOOSE_NULLABILITY,
            RETIRED_TYPE_CLASS_CONVENTION);

    public ProcessorOptions {
        schemaDirs = List.copyOf(schemaDirs);
        clientDirs = List.copyOf(clientDirs);
        errors = List.copyOf(errors);
    }

    /**
     * @param options the options of the compilation
     * @return the options, with the errors of those that are wrong
     */
    public static ProcessorOptions read(Map<String, String> options) {
        List<Finding> errors = new ArrayList<>();
        options.keySet().stream().sorted().forEach(key -> {
            if (key.startsWith(PREFIX) && !NAMES.contains(key) && !RETIRED.containsKey(key)) {
                errors.add(error(BindingCode.PROCESSOR_OPTION_UNKNOWN, "the option " + key + " is not known; the options are "
                        + new TreeSet<>(NAMES)));
            }
        });

        boolean checks = flag(options, CHECKS, true, errors);
        boolean clientChecks = flag(options, CLIENT_CHECKS, true, errors);
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
        if (checks) {
            RETIRED.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(retired -> {
                if (options.containsKey(retired.getKey())) {
                    errors.add(error(BindingCode.PROCESSOR_OPTION_REMOVED, retired.getKey() + " was renamed to " + retired.getValue()
                            + (retired.getKey().equals(REMOVED_SDL_DIR) ? ": pass the schema directories there (several are joined "
                            + "with the path separator)" : "")));
                }
            });
        }

        List<Path> directories = directories(options, SCHEMA_DIRS, errors);
        List<Path> clientDirs = directories(options, CLIENT_DIRS, errors);
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
                looseNullability, convention == null ? null : convention.strip(), clientDirs, clientChecks, errors);
    }

    private static List<Path> directories(Map<String, String> options, String name, List<Finding> errors) {
        List<Path> directories = new ArrayList<>();
        String value = options.get(name);
        if (value != null) {
            for (String entry : value.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    try {
                        directories.add(Path.of(entry.trim()));
                    } catch (InvalidPathException e) {
                        errors.add(error(BindingCode.PROCESSOR_OPTION_INVALID, name + " holds a path that this "
                                + "system cannot use: '" + entry.trim() + "' (" + e.getReason() + ")"));
                    }
                }
            }
        }
        return directories;
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
        return new Finding(code.getCode(), null, sentence, null, null, null, null);
    }
}
