package com.glassvue.domain.order.service.query;

import com.glassvue.domain.order.dto.DailySalesResponse;
import com.glassvue.domain.order.dto.ProductSalesResponse;
import com.glassvue.domain.order.dto.SalesOverviewResponse;
import com.glassvue.domain.order.dto.SalesSummaryResponse;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.domain.order.repository.OrderStatsRepository;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 매출 통계 (2026-07-24, 백로그 C-11).
 *
 * <p>집계 정의는 전부 {@link OrderStatsRepository} 의 javadoc 에 있다. 여기서는 <b>기간 경계 계산</b>과
 * <b>빈 날 채우기</b>를 맡는다 — 둘 다 SQL 에 넣으면 쿼리가 읽기 어려워지는 일이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderStatsQueryService {

    /**
     * 매출로 보는 주문 상태 — <b>명시적 열거</b>다.
     *
     * <p>{@code <> CANCELLED} 로 쓰면 나중에 추가되는 상태(환불·교환 등)가 <b>자동으로 매출에 섞인다.</b>
     * 새 상태는 여기 직접 넣도록 opt-in 으로 둔다({@code existsPurchase} 와 같은 판단).
     */
    private static final List<String> REVENUE_STATUSES = List.of(
            OrderStatus.PAID.name(), OrderStatus.SHIPPED.name(), OrderStatus.DELIVERED.name());

    /** 매출 일자는 <b>한국 시간</b> 기준이다. UTC 로 자르면 00:00~09:00 결제가 전날로 간다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int DAILY_DAYS = 30;
    private static final int TOP_PRODUCTS = 10;

    private final OrderStatsRepository statsRepository;

    public SalesOverviewResponse overview() {
        LocalDate todayKst = LocalDate.now(KST);
        Instant now = Instant.now();

        Instant todayStart = todayKst.atStartOfDay(KST).toInstant();
        Instant monthStart = todayKst.withDayOfMonth(1).atStartOfDay(KST).toInstant();
        // 전체 기간의 시작은 "충분히 과거"면 된다. Instant.EPOCH 는 1970년이라 어떤 주문보다 앞선다.
        Instant epoch = Instant.EPOCH;
        Instant dailyFrom = todayKst.minusDays(DAILY_DAYS - 1L).atStartOfDay(KST).toInstant();

        return new SalesOverviewResponse(
                summarize(todayStart, now),
                summarize(monthStart, now),
                summarize(epoch, now),
                daily(dailyFrom, todayKst),
                topProducts(epoch));
    }

    private SalesSummaryResponse summarize(Instant from, Instant to) {
        List<Object[]> rows = statsRepository.summarize(REVENUE_STATUSES, from, to);
        if (rows.isEmpty() || rows.get(0).length < 4) {
            return SalesSummaryResponse.empty();
        }
        Object[] row = rows.get(0);
        return SalesSummaryResponse.of(toLong(row[0]), toLong(row[1]), toLong(row[2]), toLong(row[3]));
    }

    /**
     * 일별 추이 — <b>매출이 0인 날을 채워서</b> 돌려준다.
     *
     * <p>SQL 은 매출이 있는 날만 준다. 그대로 화면에 보내면 차트에 구멍이 생기고,
     * "0원인 날"과 "데이터가 없는 날"을 구분할 수 없다. 날짜 생성은 SQL 보다 여기가 훨씬 싸다.
     */
    private List<DailySalesResponse> daily(Instant from, LocalDate todayKst) {
        Map<String, DailySalesResponse> found = new LinkedHashMap<>();
        for (Object[] row : statsRepository.daily(REVENUE_STATUSES, from)) {
            String date = String.valueOf(row[0]);
            found.put(date, new DailySalesResponse(date, toLong(row[1]), toLong(row[2]), toLong(row[3])));
        }

        List<DailySalesResponse> filled = new ArrayList<>(DAILY_DAYS);
        for (int i = DAILY_DAYS - 1; i >= 0; i--) {
            String date = todayKst.minusDays(i).format(DAY);
            filled.add(found.getOrDefault(date, DailySalesResponse.empty(date)));
        }
        return filled;
    }

    private List<ProductSalesResponse> topProducts(Instant from) {
        List<ProductSalesResponse> items = new ArrayList<>();
        for (Object[] row : statsRepository.topProducts(REVENUE_STATUSES, from, TOP_PRODUCTS)) {
            items.add(new ProductSalesResponse(
                    toUuid(row[0]), String.valueOf(row[1]), toLong(row[2]), toLong(row[3])));
        }
        return items;
    }

    /**
     * Oracle 의 {@code SUM}·{@code COUNT} 는 JDBC 로 {@code BigDecimal} 이 온다.
     * 네이티브 쿼리라 Hibernate 가 타입을 몰라 그대로 넘겨주므로 여기서 좁힌다.
     * (금액·건수는 전부 정수라 소수 손실이 없다.)
     */
    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.longValue();
        }
        return ((Number) value).longValue();
    }

    /** PK 는 Oracle RAW(16) 이라 네이티브 조회에서 {@code byte[]} 로 온다 — UUID 로 되돌린다. */
    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        ByteBuffer buffer = ByteBuffer.wrap((byte[]) value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
