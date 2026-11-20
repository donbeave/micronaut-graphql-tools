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
package io.micronaut.graphql.tools.visitor;

import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.inject.ast.ClassElement;

import javax.lang.model.element.AnnotationMirror;

/**
 * What only the element of the Java compiler can answer. This class is loaded only when the visitor runs under javac (a Java
 * source, or the Java stub that kapt writes for a Kotlin class): under KSP and Groovy the classes it names are not on the path.
 */
final class JavacElements {

    private static final String KOTLIN_METADATA = "kotlin.Metadata";

    private JavacElements() {
    }

    /**
     * @param type a class element that a javac-based visitor handed out
     * @return whether the class declares {@code @kotlin.Metadata}, which kapt copies from the Kotlin class into its stub
     */
    static boolean carriesKotlinMetadata(ClassElement type) {
        if (!(type.getNativeType() instanceof JavaNativeElement.Class nativeClass)) {
            return false;
        }
        for (AnnotationMirror mirror : nativeClass.element().getAnnotationMirrors()) {
            if (KOTLIN_METADATA.contentEquals(mirror.getAnnotationType().toString())) {
                return true;
            }
        }
        return false;
    }
}
