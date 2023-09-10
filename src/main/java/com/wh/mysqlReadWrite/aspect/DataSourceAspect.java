package com.wh.mysqlReadWrite.aspect;

import com.wh.mysqlReadWrite.common.DSContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据源接口切面拦截
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class DataSourceAspect { 
	
    @Pointcut("@annotation(com.wh.mysqlReadWrite.annotation.Slave) && execution(* com.wh.service.impl..*.*(..))")
    public void readPointcut(){}

    @Pointcut("@annotation(com.wh.mysqlReadWrite.annotation.Master) && execution(* com.wh.service.impl..*.*(..))")
    public void writePointcut(){}

    /**
     * 调用注解前，织人逻辑
     * @param joinPoint
     */
    @Before("readPointcut()")
    public void readBefore(JoinPoint joinPoint) {
        DSContextHolder.slave();
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        log.debug("{}.{} USE DATASOURCE SLAVE", className, methodName);
    }

    @Before("writePointcut()")
    public void writeBefore(JoinPoint joinPoint) {
        DSContextHolder.master();
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        log.debug("{}.{} USE DATASOURCE MASTER", className, methodName);
    }

    @After("readPointcut()")
    public void readAfter() {
        DSContextHolder.clear();
    }

    @After("writePointcut()")
    public void writeAfter() {
        DSContextHolder.clear();
    }
}
