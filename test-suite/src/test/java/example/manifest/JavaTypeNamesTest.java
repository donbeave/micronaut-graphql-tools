package example.manifest;

import io.micronaut.context.BeanContext;
import io.micronaut.graphql.tools.JavaTypeNames;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The binding manifest renders Java types as {@link Type#getTypeName()} renders them: from the reflected method when
 * reflection is available, otherwise from Micronaut's compile-time metadata, which keeps no wildcards, raw types or
 * generic array components.
 */
@MicronautTest(startApplication = false)
class JavaTypeNamesTest {

    @Inject
    BeanContext beanContext;

    @Test
    void returnAndParameterTypesAreRenderedAsReflectionRendersThem() {
        List<String> expected = new ArrayList<>();
        List<String> actual = new ArrayList<>();

        for (ExecutableMethod<TypeSamples, ?> method
                : beanContext.getBeanDefinition(TypeSamples.class).getExecutableMethods()) {
            Method target = method.getTargetMethod();

            expected.add(method.getMethodName() + " returns " + target.getGenericReturnType().getTypeName());
            actual.add(method.getMethodName() + " returns " + JavaTypeNames.returnType(method));

            Type[] parameterTypes = target.getGenericParameterTypes();
            List<String> parameters = JavaTypeNames.parameterTypes(method);
            for (int i = 0; i < parameterTypes.length; i++) {
                expected.add(method.getMethodName() + " parameter " + i + " " + parameterTypes[i].getTypeName());
                actual.add(method.getMethodName() + " parameter " + i + " " + parameters.get(i));
            }
        }

        expected.sort(null);
        actual.sort(null);
        assertEquals(22, expected.size());
        assertEquals(expected, actual);
    }

    @Test
    void withoutReflectionTheCompileTimeMetadataAgreesExceptForWildcardsRawTypesAndGenericArrays() {
        List<String> differences = new ArrayList<>();

        for (ExecutableMethod<TypeSamples, ?> method
                : beanContext.getBeanDefinition(TypeSamples.class).getExecutableMethods()) {
            Method target = method.getTargetMethod();
            String reflected = target.getGenericReturnType().getTypeName();
            String metadata = JavaTypeNames.of(method.getReturnType().asArgument());
            if (!reflected.equals(metadata)) {
                differences.add(method.getMethodName() + ": " + reflected + " / " + metadata);
            }
            Type[] parameterTypes = target.getGenericParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                reflected = parameterTypes[i].getTypeName();
                metadata = JavaTypeNames.of(method.getArguments()[i]);
                if (!reflected.equals(metadata)) {
                    differences.add(method.getMethodName() + " parameter " + i + ": " + reflected + " / " + metadata);
                }
            }
        }

        differences.sort(null);
        assertEquals(List.of(
                "genericArray: java.util.List<java.lang.String>[] / java.util.List[]",
                "rawList: java.util.List / java.util.List<java.lang.Object>",
                "unusual parameter 0: java.util.List<? super java.lang.Integer> / java.util.List<java.lang.Integer>",
                "unusual parameter 1: java.util.Map / java.util.Map<java.lang.Object, java.lang.Object>",
                "unusual parameter 2: java.util.List<java.lang.Long>[] / java.util.List[]",
                "wildcard: java.util.List<? extends java.lang.Number> / java.util.List<java.lang.Number>"
        ), differences);
    }

}
