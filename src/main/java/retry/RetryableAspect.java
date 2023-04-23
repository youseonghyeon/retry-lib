package retry;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
public class RetryableAspect {

    @Around("@annotation(retry.Retryable)")
    public Object retryMethod(ProceedingJoinPoint joinPoint) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method reatryableMethod = signature.getMethod();
        Retryable annotation = reatryableMethod.getAnnotation(Retryable.class);

        int retryCount = 0;

        Object proceed;
        Exception lazyException = null;

        while (retryCount <= annotation.maxAttempts()) {
            try {
                if (retryCount != 0) {
                    log.warn("Retry method...");
                    Thread.sleep(annotation.backoff());
                }
                // Proceed target method
                proceed = joinPoint.proceed();
                // Success case
                return proceed;
            } catch (Exception e) {
                lazyException = e;
                if (isAssignable(e, annotation.include()) && !isAssignable(e, annotation.exclude())) {
                    if (annotation.printStackTrace()) {
                        e.printStackTrace();
                    }
                    retryCount++;
                } else {
                    throw e;
                }
            }
        }

        // @retry.Retryable fail case

        Object target = joinPoint.getTarget();

        // Find @retry.Recover method (@retry.Retryable return type == @retry.Recover return type)
        Method recoverMethod = findRecoverMethodWithSameReturnType(target, reatryableMethod.getReturnType());

        if (recoverMethod != null) {
            log.info("retry.Recover start");
            // The exception occurring here is not retried.
            return recoverMethod.invoke(target);
        } else {
            throw lazyException;
        }
    }

    private boolean isAssignable(Exception target, Class<? extends Exception>[] compareWiths) {
        for (Class<? extends Exception> ex : compareWiths) {
            if (ex.isAssignableFrom(target.getClass())) {
                return true;
            }
        }
        return false;
    }

    public Method findRecoverMethodWithSameReturnType(Object target, Class<?> retryableMethodReturnType) {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(target.getClass());

        // Find @retry.Recover annotation
        for (Method method : methods) {
            if (method.isAnnotationPresent(Recover.class) &&
                    method.getReturnType().equals(retryableMethodReturnType)) {
                // If the return types are the same, then execute the recover logic.
                // Choose the first recover method
                return method;
            }
        }
        return null;
    }
}
