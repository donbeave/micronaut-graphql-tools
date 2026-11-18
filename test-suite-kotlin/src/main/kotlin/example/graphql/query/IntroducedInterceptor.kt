package example.graphql.query

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext

@InterceptorBean(Introduced::class)
class IntroducedInterceptor : MethodInterceptor<Any, Any> {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any = "introduced ${context.methodName}"
}
