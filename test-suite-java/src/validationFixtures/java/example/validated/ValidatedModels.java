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
package example.validated;

import example.external.ExternalModel;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.graphql.tools.annotation.GraphQLModels;

// @GraphQLModels declarations that share a compilation with Micronaut Validation's processor (#528), which re-annotates the
// classes that carry @Introspected.
public final class ValidatedModels {

    private ValidatedModels() {
    }

    @GraphQLModels(classes = {Plain.class, Listed.class, Twice.class})
    public static class Classes {
    }

    @GraphQLModels(packages = "example.validated.generated", includedAnnotations = ExternalModel.class)
    public static class Packages {
    }

    @GraphQLModels(classes = Twice.class)
    public static class Again {
    }

    @Introspected(classes = Direct.class)
    public static class DirectList {
    }

    @GraphQLModels(classes = FieldAccess.class)
    @Introspected(accessKind = Introspected.AccessKind.FIELD, includes = {"a", "b"}, excludes = {"b"})
    public static class DeclaredAccess {
    }

    public static class FieldAccess {
        public String a;
        public String b;
        public String c;
    }

    @GraphQLModels(classes = Together.class)
    @Introspected(packages = "example.validated.mixed", includedAnnotations = ExternalModel.class)
    public static class Mixed {
    }

    public static class Together {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Direct {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Twice {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Plain {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Listed {
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

}
