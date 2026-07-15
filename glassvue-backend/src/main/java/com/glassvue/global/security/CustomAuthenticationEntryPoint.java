package com.glassvue.global.security;

import com.glassvue.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 인증 필요 경로에 인증 없이 접근 시 401을 ApiResponse 포맷(JSON)으로 반환. */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        ErrorCode ec = ErrorCode.UNAUTHENTICATED;
        response.setStatus(ec.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        // 고정 구조라 라이브러리 없이 직접 작성 (message에 따옴표/역슬래시 없음)
        String body = "{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}"
                .formatted(ec.getCode(), ec.getMessage());
        response.getWriter().write(body);
    }
}
