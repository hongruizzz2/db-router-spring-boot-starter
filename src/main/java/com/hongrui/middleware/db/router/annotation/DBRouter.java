package com.hongrui.middleware.db.router.annotation;

import java.lang.annotation.*;

/**
 * @author hongrui
 * @description
 * @date 2024-11-17 13:38
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DBRouter {

    /** 分库分表字段 */
    String key() default "";

}