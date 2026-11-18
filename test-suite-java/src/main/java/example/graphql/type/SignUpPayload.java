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
package example.graphql.type;

import example.data.User;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType
public class SignUpPayload {

    private final Error error;
    private final User data;

    public SignUpPayload(Error error) {
        this.error = error;
        this.data = null;
    }

    public SignUpPayload(User data) {
        this.error = null;
        this.data = data;
    }

    public Error getError() {
        return error;
    }

    public User getData() {
        return data;
    }

}
