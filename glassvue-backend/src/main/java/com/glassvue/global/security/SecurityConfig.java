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
                        // 🔴 **공지는 관리자 콘텐츠다**(2026-08-20, BACKLOG E-4). 그전까지 여기가
                        //    `authenticated` 라 **일반 회원도 공지를 쓸 수 있었고**, 화면도 안 막았다
                        //    (「새 공지」 버튼이 `v-if="isLoggedIn"` 이었다). 문서 두 곳
                        //    (ARCHITECTURE 탈퇴 표 · 07-30 §F-1)만 「관리자 콘텐츠」라 말하고 있었다.
                        //    ⚠ 이미 쓰인 일반 회원 공지 1건은 **그대로 둔다**(검증 데이터, 사용자 결정).
                        //    그 글의 작성자는 이제 **자기 글도 못 고친다** — 의도한 결과다.
                        .requestMatchers(HttpMethod.POST, "/api/notices").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/notices/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/notices/*").hasRole("ADMIN")
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/members/**").authenticated()
                        .requestMatchers("/api/cart/**").authenticated()
                        // ⚠ 가입 쿠폰 안내는 **공개**다(G-2) — 비로그인 홈·가입 화면이 "가입하면 쿠폰"을
                        // 띄울지 판단하는 근거라 로그인 전에 읽혀야 한다. 쿠폰 **정의**(이름·할인액)만 나가고
                        // 회원별 발급분은 안 나간다. 아래 인증 규칙보다 **위에** 있어야 효력이 있다(먼저 매칭).
                        .requestMatchers(HttpMethod.GET, "/api/coupons/welcome").permitAll()
                        // ⚠ 이벤트 배너도 **공개**다(G-8) — 비로그인 혜택 스트립이 «오늘 이벤트 중» 한 줄을
                        // 띄울지 판단한다. 나가는 것은 쿠폰 **정의**와 «지금 열렸나» 뿐이고, 「이미 받았나」는
                        // 로그인했을 때만 채워진다. 🔴 **받기(POST)는 아래 인증 규칙에 걸려야 한다** —
                        // GET 만 뚫는 이유가 그것이다(경로로 뚫으면 발급이 공개된다).
                        .requestMatchers(HttpMethod.GET, "/api/coupons/event").permitAll()
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
                        // 관리자 대행 취소(B-25, 2026-08-10). ⚠ 본인 취소 /api/orders/*/cancel 과 **경로가 달라야** 한다 —
                        // 같은 경로에 역할로 분기하면 «본인 취소» 가 관리자에게도 열려 취소자 기록이 비는 행이 생긴다.
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/admin-cancel").hasRole("ADMIN")
                        // 관리자 대행 **부분** 취소(G-4, 2026-08-24). ⚠ 본인 경로(/cancel-item)와 갈라 둔다 —
                        //    admin-cancel 이 /cancel 과 갈린 것과 같은 이유다(한 경로에 두 권한을 담지 않는다).
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/admin-cancel-item").hasRole("ADMIN")
                        // 🔴 대행 반품 «요청» (2026-08-27, §I-15). §I-9 이 7일 기한을 걸면서
                        //    기한 넘긴 건을 구제할 자리가 사라졌고, 이 경로가 그 자리다.
                        //    ⚠ 이 경로만 기한을 안 본다 — 그래서 더더욱 ADMIN 으로 막혀 있어야 한다.
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/admin-return-request").hasRole("ADMIN")
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
                        // 문의: 상품 문의 목록은 공개(비밀글은 마스킹), 작성/수정/삭제는 로그인, 답변은 관리자만
                        .requestMatchers(HttpMethod.POST, "/api/inquiries/*/answer").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products/*/inquiries").authenticated()
                        // 일반 고객센터 문의·내 문의 (2026-08-07, G-3 2·3단계).
                        //
                        // ⚠ **위 「매처가 없으면 남의 것이 열린다」(쿠폰·찜·적립금)와 성격이 다르다.**
                        //   저 경로들은 컨트롤러가 소유자를 **경로/쿼리에서** 받거나 서비스가 알아서 찾아,
                        //   매처가 빠지면 실제로 남의 데이터가 나간다.
                        //   여기 둘은 컨트롤러가 `@LoginUser AuthUser`(required=true)로 받으므로
                        //   규칙을 지워도 LoginUserArgumentResolver 가 **같은 UNAUTHENTICATED 401** 을 낸다
                        //   (2026-08-07 변형 M7 로 실측 — 규칙을 통째로 지워도 테스트가 전부 통과했다).
                        //
                        //   🔴 **그래도 남겨 둔다.** 이유는 «없으면 뚫린다» 가 아니라 둘이다:
                        //   ① 인가는 **컨트롤러 앞**에서 끝나야 한다 — 리졸버 방어는 요청이 핸들러까지
                        //      들어온 뒤에 걸리므로, 앞에 무언가(필터·인터셉터)가 붙는 순간 전제가 깨진다.
                        //   ② `@LoginUser` 를 빼거나 required=false 로 바꾸는 **한 줄 수정**이
                        //      이 경로를 조용히 공개로 만든다. 그때 마지막으로 잡는 것이 이 줄이다.
                        .requestMatchers(HttpMethod.POST, "/api/inquiries").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/inquiries/me").authenticated()
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
