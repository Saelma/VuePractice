package com.glassvue.global.config;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigInteger;
import java.util.Arrays;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 페이지 크기 상한 — <b>넘으면 자르지 않고 400 으로 거절한다</b> (2026-09-18, BACKLOG H-3).
 *
 * <p>🔴 <b>스프링의 {@code max-page-size} 를 안 쓴 이유</b>: 그건 넘는 요청을 <b>조용히 잘라</b> 200 으로 답한다.
 * 부르는 쪽은 «전부 받았다» 고 믿는데 앞의 N 건만 온다 — 특히 «내 것이 목록에 <b>없다</b>» 는 단언은
 * «정말 없다» 와 «잘려서 안 보인다» 가 구분되지 않는다(WA §3-3). 실측(2026-09-18): 통합 테스트가
 * {@code size=200·500} 으로 공유 DB 전체를 받아 표본을 찾는 자리가 16곳이었다.
 * ⚠ 가드가 값을 몰래 고쳐 주는 모양이 L 축(«가드가 값을 고쳐 주고 있는 자리»)이 막은 것과 같다.
 *
 * <p>⚠ <b>{@link Pageable} 을 받는 핸들러에만</b> 건다 — {@code size} 라는 이름을 다른 뜻으로 쓰는 엔드포인트를
 * 건드리지 않기 위해서다. 목록 엔드포인트는 전부 {@code Pageable} 로 받는다(실측 34곳 · {@code size} 를 직접 받는 곳 0).
 * 새 목록도 {@code Pageable} 로 받으면 <b>자동으로</b> 걸린다 — 손목록이 없다.
 *
 * <p>⚠ 숫자가 아닌 {@code size} 는 여기서 판단하지 않는다 — 스프링이 기본 크기로 푼다(늘리는 쪽이 아니다).
 * 🔴 <b>«숫자인가» 는 스프링과 같은 기준으로 판단한다</b> — 스프링은 {@code Integer.parseInt} 로 읽어
 * {@code +500}·유니코드 숫자({@code ٥٠٠})를 <b>500 으로</b> 받는다. 처음엔 {@code \d+} 로 걸렀다가
 * 그 둘이 «숫자 아님» 으로 빠져 <b>상한을 비켜 갔다</b>(2026-09-18 /code-review). 같은 파서 규칙(`BigInteger`)으로 읽는다.
 */
@Component
public class PageSizeGuard implements HandlerInterceptor {

    /** 화면이 보내는 최대(감사 이력 DataGrid 의 {@code [20, 50, 100]})와 같다. */
    public static final int MAX_PAGE_SIZE = 100;
    private static final BigInteger MAX = BigInteger.valueOf(MAX_PAGE_SIZE);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && takesPageable(method)) {
            String raw = request.getParameter("size");
            if (raw != null && isOverLimit(raw.trim())) {
                throw new BusinessException(ErrorCode.PAGE_SIZE_TOO_LARGE);
            }
        }
        return true;
    }

    private static boolean takesPageable(HandlerMethod method) {
        return Arrays.stream(method.getMethodParameters())
                .anyMatch(p -> Pageable.class.isAssignableFrom(p.getParameterType()));
    }

    /**
     * 값으로 비교한다 — {@code BigInteger} 는 {@code Integer.parseInt} 와 같은 규칙(부호 · 유니코드 숫자 · 앞자리 0)으로 읽고
     * {@code int} 를 넘는 수도 받는다. ⚠ 그래서 {@code int} 초과는 «숫자 아님» 으로 흘러 기본 크기가 되지 않고 <b>거절</b>되며,
     * {@code 0000000000050} 은 길이가 아니라 <b>값 50</b> 으로 통과한다.
     */
    private static boolean isOverLimit(String raw) {
        try {
            return new BigInteger(raw).compareTo(MAX) > 0;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }
}
