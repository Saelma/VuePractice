package com.glassvue.global.security;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @LoginUser AuthUser 파라미터를 SecurityContext(=필터가 토큰 검증 후 넣어둔 principal)에서 주입한다.
 * 토큰은 필터가 이미 검증했으므로 여기서 다시 파싱하지 않고 SecurityContext만 읽는다.
 */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && parameter.getParameterType().equals(AuthUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        LoginUser annotation = parameter.getParameterAnnotation(LoginUser.class);
        if (annotation != null && !annotation.required()) {
            return null; // 공개 API에서 "로그인했으면" 용도
        }
        throw new BusinessException(ErrorCode.UNAUTHENTICATED);
    }
}
