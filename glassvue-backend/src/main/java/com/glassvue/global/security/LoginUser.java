package com.glassvue.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 토큰에서 꺼낸 로그인 사용자(AuthUser)를 주입한다.
 * 예) public X create(@LoginUser AuthUser user, ...)
 * required=false 면 비로그인 시 null 주입(공개 API에서 "로그인했으면" 처리용).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
    boolean required() default true;
}
