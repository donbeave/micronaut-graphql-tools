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
package io.micronaut.graphql.tools;

import graphql.GraphQL;
import io.micronaut.core.annotation.Internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Manifest;

/**
 * Reads the version of graphql-java for the header of the binding manifest. It is read when the manifest is rendered,
 * never at startup. graphql-java declares its version only in the {@code Bundle-Version} of the manifest of its jar
 * (it has no {@code Implementation-Version} and no {@code pom.properties}); the library registers
 * {@code META-INF/MANIFEST.MF} as a native image resource, so that the version is found in a native image too.
 */
@Internal
final class GraphQLJavaVersion {

    private static final String MANIFEST = "META-INF/MANIFEST.MF";

    private GraphQLJavaVersion() {
    }

    /**
     * @return the version ({@code Bundle-Version} without a {@code .0} patch level), or {@code unknown}
     */
    static String get() {
        // THROWAWAY (gate check): the return type of this method is Map on 22.4 and NormalizedVariables on 25.2, so main
        // compiled on the default leg fails with NoSuchMethodError on the ceiling and compiles fine on every leg
        try {
            graphql.execution.ValuesResolver.getNormalizedVariableValues(null, null, null, null, null);
        } catch (RuntimeException ignored) {
            // the arguments are null on purpose, only the linkage matters
        }
        try {
            ClassLoader classLoader = GraphQL.class.getClassLoader();
            Enumeration<URL> resources = classLoader.getResources(MANIFEST);
            while (resources.hasMoreElements()) {
                try (InputStream inputStream = resources.nextElement().openStream()) {
                    Manifest manifest = new Manifest(inputStream);
                    if ("graphql-java".equals(manifest.getMainAttributes().getValue("Bundle-SymbolicName"))) {
                        String version = manifest.getMainAttributes().getValue("Bundle-Version");
                        if (version != null && !version.isBlank()) {
                            return version.replaceFirst("^(\\d+\\.\\d+)\\.0$", "$1");
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // fall through: the header says unknown
        }
        return "unknown";
    }

}
