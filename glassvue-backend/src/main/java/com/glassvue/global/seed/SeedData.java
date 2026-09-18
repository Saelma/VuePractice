package com.glassvue.global.seed;

import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.auth.dto.SignupRequest;
import com.glassvue.domain.auth.service.AuthService;
import com.glassvue.domain.catalog.dto.CategoryCreateRequest;
import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.VariantRequest;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.service.CategoryService;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.security.AuthUser;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * 빈 DB 에 넣는 시작 데이터 — 최상위 관리자 1 · 일반 회원 1 · 카테고리 3 · 상품 8 (2026-09-18, BACKLOG F-3).
 *
 * <p>🔴 <b>SQL 이 아니라 서비스를 거친다</b> — PK 가 UUIDv7 RAW(16)·비밀번호가 BCrypt·상품은 옵션 최소 1개·
 * 가입은 적립금 계정을 함께 연다. SQL 로 손으로 만들면 이 규칙을 <b>베끼게</b> 되고, 규칙이 바뀌면 시드만 조용히 낡는다.
 *
 * <p>🔴 <b>최상위 관리자를 만드는 코드 경로는 여기뿐이다.</b> API 로는 SUPER_ADMIN 을 줄 수 없다
 * ({@code CANNOT_GRANT_SUPER_ADMIN}) — 그래서 새 환경은 «가입 후 DB 를 직접 UPDATE» 말고 길이 없었다(ARCHITECTURE «별도 UPDATE»).
 * ⚠ 이 클래스는 <b>빈으로 두지 않는다</b> — 운영 컨텍스트에 SUPER_ADMIN 을 만드는 빈이 떠 있을 이유가 없다.
 * {@link SeedDataRunner}({@code seed} 프로파일)만 만들어 쓰고, 테스트는 직접 만든다.
 *
 * <p>⚠ «비었나» 는 여기서 판단하지 않는다 — {@link #isEmpty()} 로 묻고 부르는 쪽이 멈춘다.
 * 테스트는 공유 DB(비어 있지 않다)에서 {@link #populate()} 를 <b>롤백 안에서</b> 부르기 때문이다.
 */
@RequiredArgsConstructor
public class SeedData {

    private final AuthService authService;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductCommandService productCommandService;
    /** 이름 뒤에 붙인다 — 운영 시드는 {@code ""}, 테스트는 공유 DB 와 안 겹치게 무작위 값. */
    private final String suffix;

    /** 만든 것 — 비밀번호는 <b>여기서만</b> 나온다(저장소에도 로그 설정에도 없다). */
    public record Result(String adminLoginId, String adminPassword, String userLoginId, String userPassword,
                         List<UUID> categoryIds, List<UUID> productIds) {
    }

    /** 회원도 상품도 하나도 없는가 — 🔴 하나라도 있으면 시드는 <b>운영 DB 를 채우는 일</b>이 된다. */
    public boolean isEmpty() {
        return memberRepository.count() == 0 && productRepository.count() == 0;
    }

    public Result populate() {
        String adminLoginId = "admin" + suffix;
        String userLoginId = "user01" + suffix;
        String adminPassword = randomPassword();
        String userPassword = randomPassword();

        MemberResponse admin = authService.signup(new SignupRequest(
                adminLoginId, adminPassword, "관리자" + suffix, adminLoginId + "@example.com", true, false));
        // 가입은 늘 USER 다 — 올리는 자리는 여기뿐이다(위 클래스 주석).
        Member adminEntity = memberRepository.findById(admin.id()).orElseThrow();
        adminEntity.changeRole(Role.SUPER_ADMIN);
        memberRepository.save(adminEntity);
        authService.signup(new SignupRequest(
                userLoginId, userPassword, "테스트회원" + suffix, userLoginId + "@example.com", true, false));

        // 상품·카테고리 등록은 감사에 남는다 — 새 환경의 감사 이력 첫 줄이 «시드» 가 된다(행위자 = 위 관리자).
        AuthUser actor = new AuthUser(admin.id(), Role.SUPER_ADMIN, "관리자" + suffix);
        UUID living = category("생활용품", actor);
        UUID kitchen = category("주방", actor);
        UUID stationery = category("문구", actor);

        List<UUID> products = new ArrayList<>();
        // 표본을 일부러 섞었다 — 정가 취소선 · 옵션 가격차 · 품절 · 재고 부족(기준 5 이하)이 화면에 한 번씩 보이게.
        products.add(product(living, "데일리 머그컵", "매일 쓰는 350ml 도자기 머그", 12_000L, 15_000L, ProductStatus.SELLING,
                List.of(new VariantRequest("화이트", 0L, 30L), new VariantRequest("블랙", 0L, 20L)), actor));
        products.add(product(living, "극세사 수건 3종", "빨아도 보송한 극세사 수건 세 장", 9_900L, null, ProductStatus.SELLING,
                List.of(new VariantRequest("기본", 0L, 50L)), actor));
        products.add(product(living, "무소음 탁상시계", "초침 소리 없는 탁상시계", 18_000L, null, ProductStatus.SOLD_OUT,
                List.of(new VariantRequest("기본", 0L, 0L)), actor));
        products.add(product(kitchen, "스테인리스 텀블러", "보온·보냉 이중 구조 텀블러", 16_500L, null, ProductStatus.SELLING,
                List.of(new VariantRequest("350ml", 0L, 15L), new VariantRequest("500ml", 2_000L, 10L)), actor));
        products.add(product(kitchen, "원목 도마", "통원목 한 장으로 만든 도마", 22_000L, null, ProductStatus.SELLING,
                List.of(new VariantRequest("기본", 0L, 12L)), actor));
        products.add(product(kitchen, "실리콘 주걱 세트", "내열 실리콘 주걱 두 개", 7_800L, null, ProductStatus.SELLING,
                List.of(new VariantRequest("기본", 0L, 3L)), actor));
        products.add(product(stationery, "스프링 노트 A5", "180쪽 스프링 노트", 3_500L, null, ProductStatus.SELLING,
                List.of(new VariantRequest("줄", 0L, 100L), new VariantRequest("무지", 0L, 80L)), actor));
        products.add(product(stationery, "젤펜 0.5 5색", "부드럽게 써지는 0.5mm 젤펜 다섯 색", 6_000L, null, ProductStatus.SELLING,
                List.of(new VariantRequest("기본", 0L, 40L)), actor));

        return new Result(adminLoginId, adminPassword, userLoginId, userPassword,
                List.of(living, kitchen, stationery), List.copyOf(products));
    }

    private UUID category(String name, AuthUser actor) {
        return categoryService.create(new CategoryCreateRequest(name + suffix), actor).id();
    }

    private UUID product(UUID categoryId, String name, String description, long price, Long listPrice,
                         ProductStatus status, List<VariantRequest> variants, AuthUser actor) {
        return productCommandService.create(new ProductCreateRequest(
                name + suffix, null, description, price, listPrice, status, categoryId, null, variants), actor);
    }

    /**
     * 16자 무작위 — 영문 대소문자·숫자. 비밀번호 정책(10자 이상 · 흔한 값 아님 · 아이디·닉네임 미포함)을 늘 통과한다.
     * ⚠ 고정값을 저장소에 두지 않는다 — 누가 이 시드로 띄운 환경을 밖에 열면 그 값이 곧 관리자 비밀번호다.
     */
    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
