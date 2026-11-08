package io.micronaut.graphql.tools.processing

import groovy.transform.CompileStatic

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

/**
 * A processor that generates one source file in the first round, as Micronaut's processor generates bean definitions
 * and AOP proxies: the generated class is a root element of the second round.
 */
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
