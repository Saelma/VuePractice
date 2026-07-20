package com.glassvue.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 무상태(JWT) 보안 설정. notice·auth·swagger·health는 공개, /api/auth/me·logout만 인증 필요.
 * (프론트 로그인·글쓰기 보호는 다음 단계에서 requestMatchers 확장)
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 공지 글쓰기(등록/수정/삭제)는 로그인 필요. 조회·조회수증가는 공개.
                        .requestMatchers(HttpMethod.POST, "/api/notices").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/notices/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/notices/*").authenticated()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/members/**").authenticated()
                        .requestMatchers("/api/cart/**").authenticated()
                        // 관리자 전용 API는 경로로 모아 한 줄로 막는다 — 엔드포인트가 늘어도 권한 설정을
                        // 빠뜨릴 수 없다(개별 매처를 잊는 사고 방지). /api/orders/** 보다 먼저 와야 한다.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 발송 처리는 관리자만(그 외 주문 API는 로그인). ship 매처를 orders/** 보다 먼저.
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/ship").hasRole("ADMIN")
                        .requestMatchers("/api/orders/**").authenticated()
                        // 이미지 업로드는 로그인만 — 포토 리뷰(2026-07-20)부터 일반 사용자도 올린다.
                        // 원래 ADMIN 전용이었지만 그건 상품 이미지가 유일한 용도였을 때 얘기다.
                        // 업로드 자체는 그룹에 붙지 않은 고아 row라 무해하고, 실제 노출은
                        // 리뷰 작성(구매 인증)·상품 등록(ADMIN)에서 걸린다. 크기는 5MB/파일·10MB/요청 제한.
                        .requestMatchers(HttpMethod.POST, "/api/images").authenticated()
                        // 카탈로그: 조회는 공개, 등록/수정/삭제는 관리자만
                        .requestMatchers(HttpMethod.POST, "/api/products", "/api/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*").hasRole("ADMIN")
                        // 리뷰: 조회는 공개, 작성/수정/삭제는 로그인 필요(구매 인증은 서비스에서)
                        .requestMatchers(HttpMethod.POST, "/api/products/*/reviews").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/reviews/*").authenticated()
                        // 문의: 목록은 공개(비밀글은 마스킹), 작성/수정/삭제는 로그인, 답변은 관리자만
                        .requestMatchers(HttpMethod.POST, "/api/inquiries/*/answer").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products/*/inquiries").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/inquiries/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/inquiries/*").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
