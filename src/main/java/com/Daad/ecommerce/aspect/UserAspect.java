package com.Daad.ecommerce.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class UserAspect {

    @Before(value = "execution(* com.Daad.ecommerce.controller..*(..))")
    public void logBeforeExecution(JoinPoint joinPoint) {
        log.info("Started executing {}.{} with args {}", joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(), joinPoint.getArgs());
    }

    @After(value = "execution(* com.Daad.ecommerce.controller..*(..))")
    public void logAfterExecution(JoinPoint joinPoint) {
        log.info("Finished executing {}.{} with args {}", joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(), joinPoint.getArgs());
    }

}
