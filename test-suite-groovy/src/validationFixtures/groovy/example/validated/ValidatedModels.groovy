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
package example.validated

import example.external.ExternalModel
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.annotation.GraphQLModels

// @GraphQLModels declarations that share a compilation with Micronaut Validation's processor (#528), which re-annotates the
// classes that carry @Introspected. The classes of the listed package are in `main`, a compilation of its own: Micronaut does
// not scan a package for Groovy classes of the same compilation.

@GraphQLModels(classes = [Plain, Listed, Twice])
class ClassModels {
}

@GraphQLModels(packages = 'example.validated.generated', includedAnnotations = [ExternalModel])
class PackageModels {
}

@GraphQLModels(classes = [Twice])
class AgainModels {
}

@Introspected(classes = [Direct])
class DirectList {
}

@GraphQLModels(classes = [FieldAccess])
@Introspected(accessKind = Introspected.AccessKind.FIELD, includes = ['a', 'b'], excludes = ['b'])
class DeclaredAccess {
}

@GraphQLModels(classes = [Together])
@Introspected(packages = 'example.validated.mixed', includedAnnotations = [ExternalModel])
class MixedModels {
}

class Together {
    String name
}

class FieldAccess {
    public String a
    public String b
    public String c
}

class Plain {
    String name
}

class Listed {
    String label
}

class Twice {
    String name
}

class Direct {
    String name
}
