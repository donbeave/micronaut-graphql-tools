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

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.inject.ExecutableMethod
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
@InterceptorBean(ElapsedTime::class)
class ElapsedTimeInterceptor : MethodInterceptor<Any, Any> {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val start = System.nanoTime()
        val result = context.proceed()
        val end = System.nanoTime()
        LOG.info("Method {} executed in {}ns.", getMethodName(context.executableMethod), end - start)
        return result
    }

    private fun getMethodName(executableMethod: ExecutableMethod<Any, Any>): String {
        val args = executableMethod.arguments.joinToString(", ") { "${it.type.name} ${it.name}" }
        return "${executableMethod.declaringType.name} ${executableMethod.name}($args)"
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ElapsedTimeInterceptor::class.java)
    }
}
