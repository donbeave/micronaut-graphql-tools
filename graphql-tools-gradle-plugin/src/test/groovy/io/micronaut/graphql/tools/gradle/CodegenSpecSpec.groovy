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
package io.micronaut.graphql.tools.gradle

import io.micronaut.graphql.tools.codegen.Language
import io.micronaut.graphql.tools.codegen.Mode
import io.micronaut.graphql.tools.codegen.ModelStyle
import io.micronaut.graphql.tools.codegen.NameConstants
import io.micronaut.graphql.tools.codegen.Options
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.lang.reflect.Method

class CodegenSpecSpec extends Specification {

    private static final Set<String> NOT_OPTIONS = ['Name', 'SchemaDirs', 'OutputDirectory', 'ResourcesDirectory', 'Class'] as Set

    def "the spec has one property for each component of the Options record, and nothing else"() {
        given:
        def components = Options.recordComponents.collect { it.name } as Set
        def properties = optionGetters().collect { name(it) } as Set

        expect:
        properties == components
    }

    def "the type of each property is the string form of the component"() {
        expect:
        Options.recordComponents.every { component ->
            def getter = optionGetters().find { name(it) == component.name }
            def type = component.type
            if (List.isAssignableFrom(type)) {
                ListProperty.isAssignableFrom(getter.returnType)
            } else if (Map.isAssignableFrom(type)) {
                MapProperty.isAssignableFrom(getter.returnType)
            } else {
                Property.isAssignableFrom(getter.returnType) && getter.genericReturnType.actualTypeArguments[0] ==
                        (type == boolean ? Boolean : String)
            }
        }
    }

    def "an empty spec contributes no option"() {
        expect:
        newSpec().toOptionMap().isEmpty()
    }

    def "a fully set spec reads back as the equal Options record"() {
        given:
        def spec = newSpec()
        spec.language.set('KOTLIN')
        spec.mode.set('SERVER')
        spec.packageName.set('com.example')
        spec.modelPackage.set('com.example.m')
        spec.resolverPackage.set('com.example.r')
        spec.namesPackage.set('com.example.n')
        spec.generatedAnnotation.set('com.example.Gen')
        spec.modelStyle.set('BEAN')
        spec.interfaceTypes.set(['Node', 'Entity'])
        spec.typeNamePrefix.set('Pre')
        spec.typeNameSuffix.set('Suf')
        spec.nullableAnnotation.set('com.example.Nullable')
        spec.rootContracts.set(false)
        spec.contractNameTemplate.set('{Field}Contract')
        spec.kotlinSuspend.set(true)
        spec.subscriptionReturnType.set('kotlinx.coroutines.flow.Flow')
        spec.typeMappings.set([DateTime: 'java.time.Instant', Url: 'java.net.URI'])
        spec.nameConstants.set('TYPES')
        spec.includes.set(['**/*.graphqls'])
        spec.excludes.set(['**/internal/**'])
        spec.commentsAsDescriptions.set(true)

        when:
        def result = Options.fromMap(spec.toOptionMap())

        then:
        result.diagnostics().empty
        result.options() == Options.builder()
                .language(Language.KOTLIN).mode(Mode.SERVER).packageName('com.example').modelPackage('com.example.m')
                .resolverPackage('com.example.r').namesPackage('com.example.n').generatedAnnotation('com.example.Gen')
                .modelStyle(ModelStyle.BEAN).interfaceTypes(['Node', 'Entity']).typeNamePrefix('Pre')
                .typeNameSuffix('Suf').nullableAnnotation('com.example.Nullable').rootContracts(false)
                .contractNameTemplate('{Field}Contract').kotlinSuspend(true)
                .subscriptionReturnType('kotlinx.coroutines.flow.Flow')
                .typeMappings([DateTime: 'java.time.Instant', Url: 'java.net.URI']).nameConstants(NameConstants.TYPES)
                .includes(['**/*.graphqls']).excludes(['**/internal/**']).commentsAsDescriptions(true).build()
    }

    def "an empty list option is the default of the generator"() {
        given:
        def spec = newSpec()
        spec.packageName.set('com.example')
        spec.includes.set([])

        expect:
        !spec.toOptionMap().keySet().any { it.startsWith('includes') }
        Options.fromMap(spec.toOptionMap()).options().includes() == Options.DEFAULT_INCLUDES
    }

    def "the default includes are the ones of the generator"() {
        expect:
        CodegenSpec.DEFAULT_INCLUDES == Options.DEFAULT_INCLUDES
    }

    private static CodegenSpec newSpec() {
        ProjectBuilder.builder().build().objects.newInstance(CodegenSpec, 'main')
    }

    private static List<Method> optionGetters() {
        CodegenSpec.methods.findAll { it.name.startsWith('get') && it.parameterCount == 0 && !(name(it).capitalize() in NOT_OPTIONS) }
    }

    private static String name(Method getter) {
        getter.name.substring(3).uncapitalize()
    }
}
