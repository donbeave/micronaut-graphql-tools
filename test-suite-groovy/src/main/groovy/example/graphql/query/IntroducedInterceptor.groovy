package example.graphql.query

import groovy.transform.CompileStatic
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext

@CompileStatic
@InterceptorBean(Introduced)
class IntroducedInterceptor implements MethodInterceptor<Object, Object> {

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        "introduced ${context.methodName}".toString()
    }
}
