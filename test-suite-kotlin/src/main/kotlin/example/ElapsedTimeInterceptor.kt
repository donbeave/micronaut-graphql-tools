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
