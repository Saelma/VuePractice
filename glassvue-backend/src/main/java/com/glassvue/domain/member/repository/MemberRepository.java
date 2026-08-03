package com.glassvue.domain.member.repository;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByLoginId(String loginId);

    /**
     * 관리자 회원 목록 검색(B-11). keyword 가 null/blank 면 전체, 아니면 loginId·nickname·email
     * 부분일치(대소문자 무시). email 은 대개 null 이라 거의 안 걸리지만 수집 시작 시 자연히 검색된다.
     */
    @Query("""
            select m from Member m
            where :keyword is null
               or lower(m.loginId)  like lower(concat('%', :keyword, '%'))
               or lower(m.nickname) like lower(concat('%', :keyword, '%'))
               or lower(m.email)    like lower(concat('%', :keyword, '%'))
            """)
    Page<Member> searchForAdmin(@Param("keyword") String keyword, Pageable pageable);

    /** 특정 역할의 회원 id 목록 — 관리자 대상 알림(재고 부족 등)에서 쓴다. */
    @Query("select m.id from Member m where m.role = :role")
    List<UUID> findIdsByRole(@Param("role") Role role);

    /**
     * 마케팅 수신에 <b>동의한</b> 회원 id 목록 (2026-08-03, B-21 후속).
     *
     * <p>⚠ 여기서 보는 것은 <b>동의(근거)뿐</b>이다. "지금 받고 싶은가"(알림 설정 토글)는
     * notification 도메인의 관심사라 여기서 알 수 없고, 알 필요도 없다 —
     * 그쪽은 {@code NotificationCommandService.create} 가 알아서 존중한다.
     * 두 조건을 한 쿼리에 합치려면 member 가 notification 테이블을 조인해야 하는데 그건 경계를 깬다.
     *
     * <p>V37 이전 가입자는 {@code marketing_agreed_at} 이 {@code null} 이라 자연히 빠진다
     * (동의를 거부한 게 아니라 <b>물어본 적이 없는</b> 사람들이다 — 그래서 안 보내는 게 맞다).
     */
    @Query("select m.id from Member m where m.marketingAgreedAt is not null")
    List<UUID> findIdsWithMarketingAgreement();

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    /** 닉네임 변경 시 본인은 제외하고 중복을 검사한다(같은 값으로 재저장 허용). */
    boolean existsByNicknameAndIdNot(String nickname, UUID id);

    /**
     * 이메일 중복 검사(B-13). ⚠ <b>소문자로 정규화된 값</b>이 들어온다고 가정한다 —
     * 저장 시점에 {@code toLowerCase} 하므로 여기서 다시 lower 를 걸지 않는다(인덱스를 타야 한다).
     */
    boolean existsByEmail(String email);

    /** 이메일 변경 시 본인 제외(같은 값 재저장 허용 — 닉네임과 같은 규칙). */
    boolean existsByEmailAndIdNot(String email, UUID id);

    /**
     * 아이디 찾기(G-1) — 주소로 회원을 찾는다. ⚠ {@link #existsByEmail} 과 같은 전제:
     * <b>소문자로 정규화된 값</b>이 들어온다(호출부가 {@code Member.normalizeEmail} 을 거친다).
     * 정규화를 빠뜨리면 대문자로 가입한 사람이 자기 아이디를 못 찾는다.
     *
     * <p>주소는 유니크라 최대 1건이다. 여러 계정을 한 주소로 묶는 건 지금 구조가 허용하지 않는다.
     */
    Optional<Member> findByEmail(String email);
}
