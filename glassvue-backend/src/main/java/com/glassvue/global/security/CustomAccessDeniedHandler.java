package com.glassvue.global.security;

import com.glassvue.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 인증은 됐지만 권한이 부족할 때(예: USER가 관리자 API 호출) 403을 ApiResponse JSON으로 반환. */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        ErrorCode ec = ErrorCode.ACCESS_DENIED;
        response.setStatus(ec.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}"
                .formatted(ec.getCode(), ec.getMessage());
        response.getWriter().write(body);
    }
}
