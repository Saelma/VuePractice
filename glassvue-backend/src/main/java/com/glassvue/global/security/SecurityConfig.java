package com.glassvue.global.security;

import jakarta.servlet.DispatcherType;
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
                        // 비동기·에러 디스패치는 재인가하지 않는다. SSE(SseEmitter)는 응답을 스트리밍한 뒤
                        // ASYNC 디스패치로 마무리되는데, 무상태(JWT)라 그 디스패치엔 SecurityContext가 없어
                        // AuthorizationFilter가 Access Denied를 낸다("응답 이미 커밋됨" 로그). 스프링 공식 해법이다.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // 공지 글쓰기(등록/수정/삭제)는 로그인 필요. 조회·조회수증가는 공개.
                        .requestMatchers(HttpMethod.POST, "/api/notices").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/notices/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/notices/*").authenticated()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/members/**").authenticated()
                        .requestMatchers("/api/cart/**").authenticated()
                        // 내 쿠폰 목록 — 기본이 permitAll이라 매처를 안 넣으면 남의 쿠폰까지 열린다.
                        // (관리자 쿠폰 API는 /api/admin/** 한 줄로 이미 막힌다.)
                        .requestMatchers("/api/coupons/**").authenticated()
                        // 찜(위시리스트)도 같은 이유 — 개인 목록이라 매처가 없으면 남의 찜이 열린다.
                        .requestMatchers("/api/wishlist/**").authenticated()
                        // 적립금·등급도 개인 정보다. 돈에 준하는 값이라 특히.
                        .requestMatchers("/api/points/**").authenticated()
                        // 알림(목록·SSE 스트림·설정)도 개인 것이라 로그인 필요. 스트림도 이 매처로 인증된다.
                        .requestMatchers("/api/notifications/**").authenticated()
                        // 재입고 알림 신청도 개인 것 — 매처가 없으면 남의 신청 목록이 열린다(B-9).
                        .requestMatchers("/api/restock/**").authenticated()
                        // 감사 이력 조회는 최상위 관리자만 — 조작 당사자(ADMIN)가 자기 이력을 보는 구조를 막는다.
                        // /api/admin/** 의 ADMIN 규칙보다 반드시 먼저 와야 좁은 규칙이 적용된다.
                        .requestMatchers("/api/admin/audit/**").hasRole("SUPER_ADMIN")
                        // 관리자 전용 API는 경로로 모아 한 줄로 막는다 — 엔드포인트가 늘어도 권한 설정을
                        // 빠뜨릴 수 없다(개별 매처를 잊는 사고 방지). /api/orders/** 보다 먼저 와야 한다.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 발송·배송완료 처리는 관리자만(그 외 주문 API는 로그인). orders/** 보다 먼저 와야 한다.
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/ship").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/deliver").hasRole("ADMIN")
                        // 반품 승인·거절도 관리자만(반품 요청은 본인이라 아래 authenticated 로 덮인다).
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/return-approve").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/return-reject").hasRole("ADMIN")
                        .requestMatchers("/api/orders/**").authenticated()
                        // 이미지 업로드는 로그인만 — 포토 리뷰(2026-07-20)부터 일반 사용자도 올린다.
                        // 원래 ADMIN 전용이었지만 그건 상품 이미지가 유일한 용도였을 때 얘기다.
                        // 업로드 자체는 그룹에 붙지 않은 고아 row라 무해하고, 실제 노출은
                        // 리뷰 작성(구매 인증)·상품 등록(ADMIN)에서 걸린다. 크기는 5MB/파일·10MB/요청 제한.
                        .requestMatchers(HttpMethod.POST, "/api/images").authenticated()
                        // 카탈로그: 조회는 공개, 등록/수정/삭제는 관리자만
                        .requestMatchers(HttpMethod.POST, "/api/products", "/api/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*", "/api/categories/*").hasRole("ADMIN")
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
