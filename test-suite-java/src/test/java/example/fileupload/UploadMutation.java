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
package example.fileupload;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLInput;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.http.multipart.CompletedFileUpload;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

// tag::resolver[]
@Requires(env = UploadFixtures.UPLOAD)
@Singleton
@GraphQLRootResolver
public class UploadMutation {

    @GraphQLInput
    public static class BookUpload {

        private String title;
        private CompletedFileUpload cover;
        private List<CompletedFileUpload> pages;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public CompletedFileUpload getCover() {
            return cover;
        }

        public void setCover(CompletedFileUpload cover) {
            this.cover = cover;
        }

        public List<CompletedFileUpload> getPages() {
            return pages;
        }

        public void setPages(List<CompletedFileUpload> pages) {
            this.pages = pages;
        }

    }

    public String uploadedFile(CompletedFileUpload file) throws IOException {
        return file.getFilename() + ":" + new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    public String uploadedBook(BookUpload input) throws IOException {
        StringBuilder result = new StringBuilder(input.getTitle()).append(" cover=")
                .append(new String(input.getCover().getBytes(), StandardCharsets.UTF_8));
        List<CompletedFileUpload> pages = input.getPages();
        if (pages != null) {
            for (CompletedFileUpload page : pages) {
                result.append(" page=").append(new String(page.getBytes(), StandardCharsets.UTF_8));
            }
        }
        return result.toString();
    }

    public String uploadedOn(CompletedFileUpload file) {
        return Thread.currentThread().getName();
    }

}
// end::resolver[]
