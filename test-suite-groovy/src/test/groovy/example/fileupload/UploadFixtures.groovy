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
package example.fileupload

import graphql.schema.idl.SchemaParser
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.customizer.GraphQLTypeDefinitionRegistryCustomizer
import jakarta.inject.Singleton

@CompileStatic
@Factory
class UploadFixtures {

    static final String UPLOAD = 'upload'

    // tag::schema[]
    @Bean
    @Singleton
    @Requires(env = UploadFixtures.UPLOAD)
    GraphQLTypeDefinitionRegistryCustomizer uploadSchema() {
        return { registry ->
            registry.merge(new SchemaParser().parse(
                    'scalar Upload\n' +
                            'extend type Mutation {\n' +
                            '  uploadedFile(file: Upload!): String!\n' +
                            '  uploadedBook(input: BookUpload!): String!\n' +
                            '  uploadedOn(file: Upload!): String!\n' +
                            '}\n' +
                            'input BookUpload {\n' +
                            '  title: String!\n' +
                            '  cover: Upload!\n' +
                            '  pages: [Upload!]\n' +
                            '}\n'))
        } as GraphQLTypeDefinitionRegistryCustomizer
    }
    // end::schema[]

}
