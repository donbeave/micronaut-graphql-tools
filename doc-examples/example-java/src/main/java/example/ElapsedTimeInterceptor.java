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
