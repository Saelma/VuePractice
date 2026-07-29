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
}
