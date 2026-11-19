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
 * under KSP, system properties under the Groovy compiler. There are three, all under {@code micronaut.graphql.tools.processor.},
 * a segment that no runtime key uses, so that the visitor rejects an unknown key there without failing on a runtime key that
 * happens to be a system property.
 *
 * @param schemaDirs the directories to read the schema from, empty when the option is absent
 * @param moduleDir  the directory of the module that sets {@code schema.dirs}, null when the option is absent; the output of
 *                   the compilation must lie inside it, or the options are those of an earlier compilation of the same JVM
 * @param checks     whether the checks run
 * @param errors     the options that are wrong; the checks do not run when there are any
 */
record ProcessorOptions(List<Path> schemaDirs, @Nullable Path moduleDir, boolean checks, List<Finding> errors) {

    static final String PREFIX = "micronaut.graphql.tools.processor.";

    static final String SCHEMA_DIRS = PREFIX + "schema.dirs";

    static final String CHECKS = PREFIX + "checks";

    static final String MODULE_DIR = PREFIX + "module.dir";

    static final Set<String> NAMES = Set.of(SCHEMA_DIRS, MODULE_DIR, CHECKS);

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

        boolean checks = true;
        String checksValue = options.get(CHECKS);
        if (checksValue != null) {
            String normalized = checksValue.trim().toLowerCase(Locale.ROOT);
            if (Set.of("false", "no", "off").contains(normalized)) {
                checks = false;
            } else if (!Set.of("true", "yes", "on").contains(normalized)) {
                errors.add(error(BindingCode.PROCESSOR_OPTION_INVALID, CHECKS + " must be true or false (or yes/no, "
                        + "on/off), not '" + checksValue + "'"));
            }
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
        return new ProcessorOptions(directories, moduleDir, checks, errors);
    }

    private static Finding error(BindingCode code, String sentence) {
        return new Finding(code.code(), null, sentence, null, null, null, null);
    }
}
