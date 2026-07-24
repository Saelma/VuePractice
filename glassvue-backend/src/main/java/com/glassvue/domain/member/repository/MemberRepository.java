package com.glassvue.domain.member.repository;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByLoginId(String loginId);

    /** 특정 역할의 회원 id 목록 — 관리자 대상 알림(재고 부족 등)에서 쓴다. */
    @Query("select m.id from Member m where m.role = :role")
    List<UUID> findIdsByRole(@Param("role") Role role);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    /** 닉네임 변경 시 본인은 제외하고 중복을 검사한다(같은 값으로 재저장 허용). */
    boolean existsByNicknameAndIdNot(String nickname, UUID id);
}
