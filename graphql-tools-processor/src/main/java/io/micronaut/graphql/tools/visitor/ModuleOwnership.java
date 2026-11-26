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
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Tells whether the options of a compilation are its own. Micronaut copies the options of a compilation into system properties
 * and a later compilation in the same JVM (the Gradle daemon, a compile worker, a Maven reactor) reads them when it sets none,
 * so a module that forgot {@code schema.dirs} sees the value of the module before it. The build also passes the directory of the
 * module as {@code module.dir}, which is stale in the same way; the output of this compilation is not, so a value whose
 * directory does not contain the output belongs to another module.
 */
@Internal
public final class ModuleOwnership {

    private static final String KEY = "micronaut.graphql.tools.processor.module-ownership";

    private ModuleOwnership() {
    }

    /**
     * What the question has found out about this compilation.
     *
     * @param option        the {@code module.dir} option that it was asked with, null when it was absent
     * @param moduleDir     the directory of the module: {@code module.dir}, else the project directory that the compiler reports,
     *                      null when there is neither
     * @param foreignOutput the output directory of this compilation when it lies outside {@code moduleDir}, null otherwise
     */
    public record Ownership(@Nullable Path option, @Nullable Path moduleDir, @Nullable Path foreignOutput) {
    }

    /**
     * Answers the question once per compilation and remembers the answer in the context, which both visitors of this module share.
     * The compiler is asked for the project directory and the class output at most once: KSP creates a file of its own to answer each
     * of them, and a second request for the same file fails the build.
     *
     * @param option  the {@code module.dir} option, null when it is absent
     * @param context the context of this compilation
     * @return the module directory and the foreign output
     */
    public static Ownership of(@Nullable Path option, VisitorContext context) {
        try {
            Ownership known = context.get(KEY, Ownership.class).orElse(null);
            if (known != null && Objects.equals(known.option(), option)) {
                return known;
            }
        } catch (RuntimeException e) {
            // a context without attributes asks the compiler each time
        }
        Path moduleDir = option != null ? option : projectDir(context);
        Ownership found = new Ownership(option, moduleDir, foreignOutput(moduleDir, context));
        try {
            context.put(KEY, found);
        } catch (RuntimeException e) {
            // see above
        }
        return found;
    }

    /**
     * @param moduleDir the directory of the module: {@code module.dir}, else the project directory that the compiler reports,
     *                  null when there is neither
     * @param context   the context of this compilation
     * @return the output directory of this compilation when it lies outside {@code moduleDir}, null when it lies inside or when
     *         the compiler gives no output directory (kapt and KSP do not), so that the check does not apply
     */
    private static @Nullable Path foreignOutput(@Nullable Path moduleDir, VisitorContext context) {
        if (moduleDir == null) {
            return null;
        }
        Path output;
        try {
            output = context.getClassesOutputPath().map(it -> it.toAbsolutePath().normalize()).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
        return output == null || real(output).startsWith(real(moduleDir)) ? null : output;
    }

    /**
     * @param context the context of this compilation
     * @return the project directory that the compiler reports, null when it reports none
     */
    private static @Nullable Path projectDir(VisitorContext context) {
        try {
            return context.getProjectDir().map(it -> it.toAbsolutePath().normalize()).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // a symbolic link in one of the two paths (a temporary directory under /var on macOS) must not make them differ
    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException e) {
            return path;
        }
    }
}
