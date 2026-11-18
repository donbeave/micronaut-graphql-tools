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
package io.micronaut.graphql.tools.processing

import groovy.transform.CompileStatic

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

@CompileStatic
class GeneratingProcessor extends AbstractProcessor {

    private final String className
    private final String source
    private boolean generated

    GeneratingProcessor(String className, String source) {
        this.className = className
        this.source = source
    }

    @Override
    Set<String> getSupportedAnnotationTypes() {
        return ['*'] as Set
    }

    @Override
    SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported()
    }

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!generated) {
            generated = true
            processingEnv.filer.createSourceFile(className).openWriter().withCloseable { it.write(source) }
        }
        return false
    }

}
