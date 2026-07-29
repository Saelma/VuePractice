package com.glassvue.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.member.service.command.MemberAddressCommandService;
import com.glassvue.domain.member.service.query.MemberAddressQueryService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.JwtProvider;
import com.glassvue.global.security.RefreshTokenStore;
import com.glassvue.global.security.TokenBlacklist;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository memberRepository;
    // V18 이후 MemberResponse.ship* 는 주소록에서 온다 — 스텁 없이 null 을 돌려주면 "기본 배송지 없음"이다.
    @Mock MemberAddressCommandService addressCommandService;
    @Mock MemberAddressQueryService addressQueryService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock TokenBlacklist tokenBlacklist;
    @Mock JwtProvider jwtProvider;
    @InjectMocks MemberService service;

    private final UUID memberId = UUID.randomUUID();

    private Member member() {
        return Member.builder().loginId("kim").password("HASH").nickname("김철수").role(Role.USER).build();
    }

    @Test
    @DisplayName("비번 변경: 현재 비번 불일치 → PASSWORD_MISMATCH, 변경·세션무효화 안 함")
    void changePassword_mismatch() {
        Member m = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("wrong", "HASH")).thenReturn(false);
        assertThatThrownBy(() -> service.changePassword(memberId, "wrong", "newpw"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
        assertThat(m.getPassword()).isEqualTo("HASH");
        verify(refreshTokenStore, never()).delete(memberId);
    }

    @Test
    @DisplayName("비번 변경: 일치 → 새 해시 저장 + 리프레시 토큰 무효화")
    void changePassword_ok() {
        Member m = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("cur", "HASH")).thenReturn(true);
        when(passwordEncoder.encode("newpw")).thenReturn("NEWHASH");
        service.changePassword(memberId, "cur", "newpw");
        assertThat(m.getPassword()).isEqualTo("NEWHASH");
        verify(refreshTokenStore).delete(memberId);
    }

    @Test
    @DisplayName("닉네임 변경: 반영 + 응답")
    void changeNickname() {
        Member m = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(m));
        when(memberRepository.existsByNicknameAndIdNot("새닉네임", memberId)).thenReturn(false);
        var res = service.changeNickname(memberId, "새닉네임");
        assertThat(m.getNickname()).isEqualTo("새닉네임");
        assertThat(res.nickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("닉네임 변경: 남이 쓰는 닉네임 → DUPLICATE_NICKNAME, 반영 안 함")
    void changeNickname_duplicate() {
        Member m = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(m));
        when(memberRepository.existsByNicknameAndIdNot("중복닉", memberId)).thenReturn(true);
        assertThatThrownBy(() -> service.changeNickname(memberId, "중복닉"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        assertThat(m.getNickname()).isEqualTo("김철수");
    }

    // ---------- 이메일 등록·변경 (B-13) ----------

    @Test
    @DisplayName("이메일 변경: 대소문자·공백이 섞여도 소문자 trim 으로 저장된다")
    void changeEmail_normalizes() {
        Member m = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(m));
        when(memberRepository.existsByEmailAndIdNot("hong@example.com", memberId)).thenReturn(false);

        service.changeEmail(memberId, "  Hong@Example.COM  ");

        // ⚠ 검사에 쓴 값과 저장한 값이 같아야 한다 — 어긋나면 유니크 제약이 중복을 못 거른다.
        assertThat(m.getEmail()).isEqualTo("hong@example.com");
    }

    @Test
    @DisplayName("이메일 변경: 남이 쓰는 주소 → DUPLICATE_EMAIL, 반영 안 함")
    void changeEmail_duplicate() {
        Member m = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(m));
        when(memberRepository.existsByEmailAndIdNot("taken@example.com", memberId)).thenReturn(true);

        assertThatThrownBy(() -> service.changeEmail(memberId, "Taken@Example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        assertThat(m.getEmail()).isNull();
    }

    @Test
    @DisplayName("이메일 변경: 본인이 쓰던 같은 주소 재저장은 허용(본인 제외 검사)")
    void changeEmail_sameValueAllowed() {
        Member m = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(m));
        // existsByEmailAndIdNot 이 본인을 제외하므로 false — 닉네임과 같은 규칙
        when(memberRepository.existsByEmailAndIdNot("mine@example.com", memberId)).thenReturn(false);

        service.changeEmail(memberId, "mine@example.com");
        service.changeEmail(memberId, "mine@example.com");

        assertThat(m.getEmail()).isEqualTo("mine@example.com");
    }

    @Test
    @DisplayName("없는 회원 → MEMBER_NOT_FOUND")
    void notFound() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeNickname(memberId, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
