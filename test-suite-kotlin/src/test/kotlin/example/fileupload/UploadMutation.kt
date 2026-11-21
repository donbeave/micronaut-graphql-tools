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

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.http.multipart.CompletedFileUpload
import jakarta.inject.Singleton

@GraphQLInput
class BookUpload {
    lateinit var title: String
    lateinit var cover: CompletedFileUpload
    var pages: List<CompletedFileUpload>? = null
}

// tag::resolver[]
@Requires(env = [UPLOAD])
@Singleton
@GraphQLRootResolver
open class UploadMutation {

    open fun uploadedFile(file: CompletedFileUpload): String = file.filename + ":" + String(file.bytes, Charsets.UTF_8)

    open fun uploadedBook(input: BookUpload): String =
        input.title + " cover=" + String(input.cover.bytes, Charsets.UTF_8) +
            (input.pages ?: emptyList()).joinToString("") { " page=" + String(it.bytes, Charsets.UTF_8) }

    open fun uploadedOn(file: CompletedFileUpload): String = Thread.currentThread().name
}
// end::resolver[]
