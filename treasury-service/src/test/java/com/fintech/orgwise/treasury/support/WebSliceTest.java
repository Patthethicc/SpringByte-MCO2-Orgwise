package com.fintech.orgwise.treasury.support;

import com.fintech.orgwise.treasury.exception.GlobalExceptionHandler;
import com.fintech.orgwise.treasury.security.JwtAuthenticationFilter;
import com.fintech.orgwise.treasury.security.JwtTokenService;
import com.fintech.orgwise.treasury.security.RestAccessDeniedHandler;
import com.fintech.orgwise.treasury.security.RestAuthenticationEntryPoint;
import com.fintech.orgwise.treasury.security.SecurityConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Imports the real security chain and error handling into {@code @WebMvcTest} slices. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
public @interface WebSliceTest {
}
