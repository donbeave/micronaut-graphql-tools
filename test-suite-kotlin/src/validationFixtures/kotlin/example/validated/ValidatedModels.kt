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
// classes that carry @Introspected.

@GraphQLModels(classes = [Plain::class, Listed::class, Twice::class])
class ClassModels

@GraphQLModels(packages = ["example.validated.generated"], includedAnnotations = [ExternalModel::class])
class PackageModels

@GraphQLModels(classes = [Twice::class])
class AgainModels

@Introspected(classes = [Direct::class])
class DirectList

@GraphQLModels(classes = [FieldAccess::class])
@Introspected(accessKind = [Introspected.AccessKind.FIELD])
class DeclaredAccess

@GraphQLModels(classes = [Filtered::class])
@Introspected(includes = ["a", "b"], excludes = ["b"])
class DeclaredFilter

@GraphQLModels(classes = [Together::class])
@Introspected(packages = ["example.validated.mixed"], includedAnnotations = [ExternalModel::class])
class MixedModels

class Together(var name: String = "")

class FieldAccess(var a: String = "")

class Filtered(var a: String = "", var b: String = "", var c: String = "")

class Plain(var name: String = "")

class Listed(var label: String = "")

class Twice(var name: String = "")

class Direct(var name: String = "")
