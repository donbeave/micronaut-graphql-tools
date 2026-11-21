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
package io.micronaut.graphql.tools.upload;

/**
 * The names that the upload module uses: the scalar of the schema, the configuration key and the error code of a multipart
 * request that does not follow the specification.
 */
public final class GraphQLUpload {

    /** The prefix of the configuration keys of the module. */
    public static final String PREFIX = "micronaut.graphql.tools.upload";

    /** The name of the scalar in the schema: declare it with {@code scalar Upload}. */
    public static final String SCALAR_NAME = "Upload";

    /**
     * The {@code code} extension of the error in the body of a {@code 400 Bad Request} that answers a multipart request that
     * is not valid: a missing or malformed {@code operations} or {@code map} part, a path that does not exist in the variables,
     * or a part that the map names and the request does not have.
     */
    public static final String INVALID_REQUEST_CODE = "INVALID_MULTIPART_REQUEST";

    private GraphQLUpload() {
    }

}
