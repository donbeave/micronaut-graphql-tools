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
package io.micronaut.graphql.tools.schema;

final class MostSpecificFixture {

    private MostSpecificFixture() {
    }

    public static class Base {
    }

    public static class Entity extends Base {
    }

    public static class Proxy extends Entity {
    }

    public interface Left {
    }

    public interface Right {
    }

    public static class OnlyLeft implements Left {
    }

    public static class OnlyRight implements Right {
    }

    public static class Both implements Left, Right {
    }

}
