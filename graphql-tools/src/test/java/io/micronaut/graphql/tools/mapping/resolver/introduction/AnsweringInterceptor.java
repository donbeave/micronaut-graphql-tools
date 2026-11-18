package io.micronaut.graphql.tools.mapping.resolver.introduction;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.aop.InterceptorBean;

@InterceptorBean(Answering.class)
@Requires(property = "spec.name", value = "IntroductionResolverSpec")
class AnsweringInterceptor implements MethodInterceptor<Object, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "introduced " + context.getMethodName();
    }

}
