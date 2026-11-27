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
package io.micronaut.graphql.tools.codegen.schema

import io.micronaut.graphql.tools.codegen.Language
import io.micronaut.graphql.tools.codegen.Options
import io.micronaut.graphql.tools.codegen.golden.GoldenFiles
import io.micronaut.graphql.tools.codegen.ir.IrSnapshot
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path
import java.nio.file.Paths

/**
 * The IR text snapshots of the sample schema: object, interface (also one that implements an interface), union, enum, input,
 * extensions (some before their base definition), custom scalars, root contracts, directives, deprecation and defaults. Run the
 * suite with -PupdateGolden to rewrite the snapshots, and review the diff.
 */
class IrGoldenSpec extends Specification {

    static final Path SAMPLE = Paths.get(IrGoldenSpec.getResource('/schema/sample').toURI())

    @Unroll
    def "the IR of the sample schema with #name options matches its snapshot"() {
        when:
        def result = IrBuilder.fromDirectory(SAMPLE, options)

        then:
        result.diagnostics().empty
        result.skippedFiles() == ['client.graphql']
        matches(name, IrSnapshot.print(result.ir()))

        where:
        name     | options
        'default' | base().build()
        'styled' | base().language(Language.KOTLIN).kotlinSuspend(true).typeNamePrefix('Gql').typeNameSuffix('Dto')
                .interfaceTypes(['Review']).resolverPackage('com.example.api').contractNameTemplate('{Operation}{Field}')
                .subscriptionReturnType('kotlinx.coroutines.flow.Flow')
                .typeMappings(MAPPINGS + ['Product.price': 'java.lang.String', 'Category': 'com.example.ext.Category']).build()
    }

    static final Map<String, String> MAPPINGS = [Date: 'java.time.LocalDate', Money: 'java.math.BigDecimal']

    static Options.Builder base() {
        Options.builder().packageName('com.example').typeMappings(MAPPINGS)
    }

    private static boolean matches(String name, String actual) {
        new GoldenFiles().assertText("ir-sample-${name}", 'ir.txt', actual)
        true
    }
}
