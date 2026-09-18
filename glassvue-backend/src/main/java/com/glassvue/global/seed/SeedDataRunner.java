package com.glassvue.global.seed;

import com.glassvue.domain.auth.service.AuthService;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.service.CategoryService;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code seed} 프로파일로 띄우면 <b>빈 DB 에 시작 데이터를 넣고 끝난다</b> (2026-09-18, BACKLOG F-3).
 *
 * <p>🔴 <b>{@code dev} 프로파일에 묶지 않은 이유</b>: 이 저장소의 dev 는 운영과 <b>같은 계정·같은 {@code espdb}</b> 에 붙는다
 * ({@code application-dev.yml} 은 p6spy 로 감쌀 뿐이다). «dev 면 시드» 로 두면 dev 백엔드를 띄우는 순간 운영에 들어간다.
 * 그래서 <b>따로 켜야만</b> 돈다 — 그리고 켜더라도 {@link SeedData#isEmpty()} 가 아니면 <b>아무것도 안 하고 1 로 끝난다.</b>
 *
 * <p>⚠ <b>한 번 돌고 끝난다</b>(성공 0 · 거절 1). 시드를 넣은 뒤엔 평소처럼 띄운다 — 서버로 남겨 두면
 * «seed 로 떠 있는 서버» 가 생기고 다음에 누가 그걸 그대로 재기동한다.
 * Flyway 는 러너보다 <b>먼저</b> 돈다 — 빈 스키마면 마이그레이션이 테이블을 만든 뒤 여기로 온다.
 *
 * <p>실행: {@code infra/README} 「처음부터 세울 때」 참조.
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class SeedDataRunner implements ApplicationRunner {

    private final AuthService authService;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductCommandService productCommandService;
    private final ConfigurableApplicationContext context;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        SeedData seed = new SeedData(authService, memberRepository, productRepository,
                categoryService, productCommandService, "");
        if (!seed.isEmpty()) {
            log.error("[시드] 거절 — 이 DB 는 비어 있지 않다(회원 {} · 상품 {}). 시드는 **빈 DB 에만** 넣는다. 아무것도 안 바꿨다.",
                    memberRepository.count(), productRepository.count());
            exit(1);
            return;
        }
        // 🔴 **한 트랜잭션으로 넣는다**(2026-09-18 /code-review). 서비스마다 따로 커밋되면 중간에 죽었을 때
        //    관리자 등이 반쯤 남고, 그러면 {@code isEmpty()} 가 거짓이 되어 **다시 돌려도 영영 거절**된다 —
        //    찍기 전이던 비밀번호도 잃는다. 묶어 두면 실패는 «아무것도 안 들어감» 이라 다시 돌리면 된다.
        //    ⚠ 가입 후처리(AFTER_COMMIT — 가입 쿠폰)는 전체가 커밋된 뒤 한 번에 돈다.
        SeedData.Result r = new TransactionTemplate(transactionManager).execute(status -> seed.populate());
        // ⚠ 비밀번호가 나오는 곳은 여기 한 번뿐이다 — 지금 적어 두지 않으면 다시 볼 방법이 없다(재설정해야 한다).
        log.warn("[시드] 완료 — 카테고리 {} · 상품 {}. 아래 두 계정은 **지금 적어 둔다**:", r.categoryIds().size(), r.productIds().size());
        log.warn("[시드]   최상위 관리자  {} / {}", r.adminLoginId(), r.adminPassword());
        log.warn("[시드]   일반 회원      {} / {}", r.userLoginId(), r.userPassword());
        exit(0);
    }

    private void exit(int code) {
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
