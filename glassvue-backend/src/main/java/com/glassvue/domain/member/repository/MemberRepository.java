package com.glassvue.domain.member.repository;

import com.glassvue.domain.member.entity.Member;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    /** 닉네임 변경 시 본인은 제외하고 중복을 검사한다(같은 값으로 재저장 허용). */
    boolean existsByNicknameAndIdNot(String nickname, UUID id);
}
