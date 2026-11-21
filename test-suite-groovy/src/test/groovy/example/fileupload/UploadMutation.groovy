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

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.http.multipart.CompletedFileUpload
import jakarta.inject.Singleton

@CompileStatic
@GraphQLInput
class BookUpload {
    String title
    CompletedFileUpload cover
    List<CompletedFileUpload> pages
}

// tag::resolver[]
@CompileStatic
@Requires(env = UploadFixtures.UPLOAD)
@Singleton
@GraphQLRootResolver
class UploadMutation {

    String uploadedFile(CompletedFileUpload file) {
        return file.filename + ':' + new String(file.bytes, 'UTF-8')
    }

    String uploadedBook(BookUpload input) {
        String result = input.title + ' cover=' + new String(input.cover.bytes, 'UTF-8')
        for (CompletedFileUpload page : (input.pages ?: [] as List<CompletedFileUpload>)) {
            result += ' page=' + new String(page.bytes, 'UTF-8')
        }
        return result
    }

    String uploadedOn(CompletedFileUpload file) {
        return Thread.currentThread().name
    }

}
// end::resolver[]
