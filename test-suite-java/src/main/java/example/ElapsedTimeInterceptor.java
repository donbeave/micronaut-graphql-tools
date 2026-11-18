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
package example;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

@Singleton
@InterceptorBean(ElapsedTime.class)
public class ElapsedTimeInterceptor implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(ElapsedTimeInterceptor.class);

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        long start = System.nanoTime();
        Object result = context.proceed();
        long end = System.nanoTime();
        LOG.info(
                "Method {} executed in {}ns.",
                getMethodName(context.getExecutableMethod()),
                (end - start)
        );
        return result;
    }

    private String getMethodName(ExecutableMethod<Object, Object> executableMethod) {
        String args = Arrays.stream(executableMethod.getArguments())
                .map(arg -> arg.getTypeString(true) + " " + arg.getName())
                .collect(Collectors.joining(", "));
        return executableMethod.getDeclaringType().getName() + " " + executableMethod.getName() + "(" + args + ")";
    }

}
