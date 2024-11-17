package com.hongrui.middleware.db.router.annotation;

import java.lang.annotation.*;

/**
 * @author hongrui
 * @description
 * @date 2024-11-17 15:21
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DBRouterStrategy {

    boolean splitTable() default false;

}
