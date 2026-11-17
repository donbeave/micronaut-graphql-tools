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
