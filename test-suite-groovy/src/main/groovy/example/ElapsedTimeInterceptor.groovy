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
package example

import groovy.transform.CompileStatic
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.inject.ExecutableMethod
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
@Singleton
@InterceptorBean(ElapsedTime)
class ElapsedTimeInterceptor implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(ElapsedTimeInterceptor)

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        long start = System.nanoTime()
        Object result = context.proceed()
        long end = System.nanoTime()
        LOG.info('Method {} executed in {}ns.', getMethodName(context.executableMethod), (end - start))
        return result
    }

    private static String getMethodName(ExecutableMethod<Object, Object> executableMethod) {
        String args = executableMethod.arguments.collect { it.type.name + ' ' + it.name }.join(', ')
        return executableMethod.declaringType.name + ' ' + executableMethod.name + '(' + args + ')'
    }
}
