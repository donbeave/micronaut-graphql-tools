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
/**
 * The names of the upload module: the {@code Upload} scalar, the configuration key and the error code of an invalid multipart
 * request. The module adds a multipart route for the GraphQL multipart request specification and the {@code Upload} scalar,
 * which an argument or an input object field declares as {@code CompletedFileUpload} or {@code CompletedPart}.
 *
 * <p><b>Audience.</b> Public API. An application reads the constants; it never calls into the implementation.</p>
 */
package io.micronaut.graphql.tools.upload;
