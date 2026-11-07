package com.aloc.sai.security;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 접근 토큰의 사용자 ID를 Long으로 받는다. JWT 필터가 subject(userId)를 username에 넣는다.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(username)")
public @interface LoginUserId {
}
