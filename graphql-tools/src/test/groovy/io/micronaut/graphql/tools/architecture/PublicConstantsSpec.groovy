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
package io.micronaut.graphql.tools.architecture

import io.micronaut.graphql.tools.binding.internal.manifest.ManifestRenderer
import io.micronaut.graphql.tools.binding.internal.schema.SchemaLocation
import io.micronaut.graphql.tools.config.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.manifest.BindingManifest
import spock.lang.Specification

/** The public types hold their own constants, so the bytecode of a public class never reaches into an internal package. */
class PublicConstantsSpec extends Specification {

    void "the default schema location of the configuration is the one the binding module resolves"() {
        expect:
        GraphQLToolsConfiguration.SchemaConfiguration.DEFAULT_SCHEMA_LOCATION == SchemaLocation.DEFAULT
        new GraphQLToolsConfiguration().schema.locations == [GraphQLToolsConfiguration.SchemaConfiguration.DEFAULT_SCHEMA_LOCATION]
    }

    void "the manifest format of the public type is the one the renderer writes"() {
        expect:
        BindingManifest.FORMAT == ManifestRenderer.FORMAT
    }

    void "no source file of a public package imports an internal package"() {
        given:
        def root = new File('src/main/java/io/micronaut/graphql/tools')
        def offenders = root.directory ? importsOfInternal(root) : null

        expect:
        offenders != null
        offenders.isEmpty()
    }

    void "the import scan names a public source that imports an internal package"() {
        given:
        def dir = File.createTempDir()
        def pub = new File(dir, 'api')
        pub.mkdirs()
        new File(pub, 'Leak.java').text = 'package api;\nimport io.micronaut.graphql.tools.binding.internal.schema.SchemaLocation;\nclass Leak {}\n'

        expect:
        importsOfInternal(dir).any { it.contains('Leak.java') }

        cleanup:
        dir.deleteDir()
    }

    private static List<String> importsOfInternal(File root) {
        List<String> found = []
        root.eachFileRecurse { File f ->
            if (f.name.endsWith('.java') && f.name != 'package-info.java') {
                String relative = root.toPath().relativize(f.toPath()).toString()
                boolean internalSource = 'internal' in relative.split('/').toList()
                if (!internalSource) {
                    f.eachLine { line ->
                        if (!line.endsWith('Condition;') && line =~ /^import\s+(static\s+)?[\w.]*\binternal\b/) {
                            found << "${f.path}: ${line}".toString()
                        }
                    }
                }
            }
        }
        found
    }

}
