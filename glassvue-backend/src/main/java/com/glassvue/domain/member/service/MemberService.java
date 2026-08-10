package com.glassvue.domain.member.service;

import com.glassvue.domain.auth.dto.MemberResponse;
import com.glassvue.domain.member.dto.ShippingAddressRequest;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.event.MemberWithdrawnEvent;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.member.service.command.MemberAddressCommandService;
import com.glassvue.domain.member.service.query.MemberAddressQueryService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.mail.Mailer;
import com.glassvue.global.security.EmailVerificationCodeStore;
import com.glassvue.global.security.JwtProvider;
import com.glassvue.global.security.PasswordPolicy;
import com.glassvue.global.security.RefreshTokenStore;
import com.glassvue.global.security.TokenBlacklist;
import com.glassvue.global.security.TokenRevocationStore;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 계정 관리 — 닉네임/비밀번호 변경, 회원 탈퇴. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberAddressCommandService addressCommandService;
    private final MemberAddressQueryService addressQueryService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;
    private final TokenRevocationStore tokenRevocationStore;
    private final PasswordPolicy passwordPolicy;
    private final JwtProvider jwtProvider;
    private final EmailVerificationCodeStore emailVerificationCodeStore;
    private final Mailer mailer;
    private final ApplicationEventPublisher eventPublisher;

    /** 관리자 회원 id 목록 — 관리자 대상 알림(재고 부족 등)을 만들 때 쓰는 다른 도메인용 공개 API. */
    @Transactional(readOnly = true)
    public java.util.List<UUID> adminIds() {
        return memberRepository.findIdsByRole(Role.ADMIN);
    }

    /**
     * 마케팅 수신에 <b>동의한</b> 회원 id 목록 — notification 도메인용 공개 API (2026-08-03, B-21 후속).
     *
     * <p>{@link #adminIds()} 와 같은 자리다: <b>대상 선정의 근거는 member 가 갖고, 알림을 만드는 일은
     * notification 이 한다.</b> 그래야 notification 이 member 테이블을 직접 만지지 않는다(도메인 경계).
     *
     * <p>⚠ 여기서 주는 것은 <b>"동의한 사람" 전부</b>이고 <b>"보낼 사람"이 아니다.</b>
     * 알림 설정에서 마케팅을 끈 사람은 이 목록에 <b>그대로 들어 있고</b>, 걸러내는 것은
     * {@code NotificationCommandService.create} 다. 동의(근거)와 수신 거부(선호)는 다른 것이라
     * 판단하는 주체도 다르다.
     */
    @Transactional(readOnly = true)
    public java.util.List<UUID> marketingAgreedIds() {
        return memberRepository.findIdsWithMarketingAgreement();
    }

    /** 정지 여부 — 주문(order) 도메인이 정지 회원의 주문을 막을 때 쓰는 공개 API(B-11 후속). */
    @Transactional(readOnly = true)
    public boolean isSuspended(UUID memberId) {
        return find(memberId).isSuspended();
    }

    /**
     * loginId 조회 — 감사 기록의 {@code target_login} <b>스냅샷용</b> 공개 API (2026-08-10, B-25).
     *
     * <p>관리자 대행 주문 취소가 감사를 남길 때 대상(주문자)의 loginId 가 필요한데, order 도메인은
     * {@code member} 테이블을 직접 읽지 않는다(도메인 간 직접 참조 금지). {@code isSuspended} 와
     * <b>같은 성격의 창구</b>다 — 엔티티가 아니라 <b>필요한 값 하나만</b> 내준다.
     *
     * <p>⚠ 주문에는 {@code buyer_nickname} 스냅샷이 있지만 <b>그걸 쓰면 안 된다</b> — 감사 테이블의
     * 열 이름은 {@code target_login} 이고, 거기에 닉네임을 넣으면 <b>타입은 맞고 뜻이 틀린 값</b>이
     * 원장에 남는다. 나중에 loginId 로 조회하는 사람이 못 찾는다.
     */
    @Transactional(readOnly = true)
    public String loginIdOf(UUID memberId) {
        return find(memberId).getLoginId();
    }

    public MemberResponse changeNickname(UUID memberId, String nickname) {
        Member member = find(memberId);
        // 닉네임은 유니크. 본인은 제외해 같은 값 재저장은 허용하고, 남이 쓰는 값이면 막는다.
        if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        member.updateNickname(nickname);
        // 주의: 토큰의 nickname claim은 다음 로그인/refresh 때 갱신됨(과거 글의 작성자명은 그대로).
        return withDefaultAddress(member);
    }

    /**
     * 이메일 등록·변경(B-13). 기존 회원은 값이 없어(전원 NULL) <b>이 화면이 유일한 수집 경로</b>다.
     *
     * <p>⚠ <b>저장만 하고 확인 메일을 자동 발송하지는 않는다</b>(B-14). 인증은 사용자가 화면에서
     * 「인증메일 보내기」를 눌러 시작한다 — 주소를 고치다 만 상태에서 메일이 나가는 걸 막고,
     * 재발송 버튼과 경로를 하나로 유지하기 위해서다.
     * {@link Member#updateEmail} 이 <b>인증 상태를 자동으로 푼다</b>(주소가 바뀌면 인증도 무효).
     */
    public MemberResponse changeEmail(UUID memberId, String email) {
        Member member = find(memberId);
        String normalized = Member.normalizeEmail(email);
        // 닉네임과 같은 규칙 — 본인은 제외해 같은 값 재저장은 허용하고, 남이 쓰는 값이면 막는다.
        if (memberRepository.existsByEmailAndIdNot(normalized, memberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        member.updateEmail(normalized);
        log.info("Email updated: {}", memberId); // 주소 자체는 로그에 남기지 않는다(개인정보)
        return withDefaultAddress(member);
    }

    /**
     * 이메일 소유 인증 — 인증번호 발송 (B-14, 2026-07-29).
     *
     * <p>⚠ <b>이미 인증된 주소면 아무것도 하지 않는다</b> — 재발송은 코드를 새로 만들고 시도 횟수를
     * 리셋하므로, 인증된 상태에서 부르면 얻는 것 없이 메일만 나간다.
     *
     * <p>⚠ 이메일이 없으면 보낼 곳이 없다 → {@code EMAIL_REQUIRED}. 여기는 <b>본인 요청</b>이라
     * 열거 방지가 걸리지 않는다(재설정과 다르다) — 사용자에게 "먼저 이메일을 등록하라"고 알려야 한다.
     */
    public void sendEmailVerification(UUID memberId) {
        Member member = find(memberId);
        if (member.getEmail() == null) {
            throw new BusinessException(ErrorCode.EMAIL_REQUIRED);
        }
        if (member.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        String code = emailVerificationCodeStore.issue(memberId);
        mailer.send(member.getEmail(), "[Glassvue] 이메일 인증번호", """
                안녕하세요, %s님.

                아래 인증번호를 설정 화면에 입력해 주세요.

                    %s

                이 번호는 10분 후 만료되며, 5회 틀리면 폐기됩니다(다시 요청하세요).
                본인이 요청한 것이 아니라면 이 메일을 무시하세요.
                """.formatted(member.getNickname(), code));
        log.info("Email verification code issued: {}", memberId); // 코드·주소는 남기지 않는다
    }

    /**
     * 이메일 소유 인증 — 인증번호 확인 (B-14).
     *
     * <p>⚠ 틀린 코드는 {@code INVALID_VERIFICATION_CODE} 하나로만 답한다 — "만료됐다/횟수 초과다"를
     * 구분해 주면 공격자가 <b>남은 시도 횟수를 세어</b> 재발송 타이밍을 맞출 수 있다.
     */
    public MemberResponse confirmEmailVerification(UUID memberId, String code) {
        Member member = find(memberId);
        if (!emailVerificationCodeStore.verify(memberId, code)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        member.verifyEmail();
        log.info("Email verified: {}", memberId);
        return withDefaultAddress(member);
    }

    /**
     * 기본 배송지 저장 — 주문서에 자동으로 채워 넣기 위한 값. 주문에는 복사(스냅샷)된다.
     *
     * <p>2026-07-24(V18)부터 <b>저장 위치가 주소록</b>이다. {@code member.ship_*} 컬럼에 쓰던 것을
     * 기본 배송지 항목 upsert 로 바꿨다 — API 계약(경로·요청·응답)은 그대로라 화면은 손대지 않았다.
     */
    public MemberResponse updateShippingAddress(UUID memberId, ShippingAddressRequest req) {
        Member member = find(memberId);
        addressCommandService.saveDefault(memberId, req);
        return withDefaultAddress(member);
    }

    public void changePassword(UUID memberId, String currentPassword, String newPassword) {
        Member member = find(memberId);
        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        // ⚠ 정책 검사는 **현재 비밀번호 확인 뒤**다. 앞에 두면 남이 아무 값이나 넣어 보며
        // "이 비밀번호는 정책에 걸린다/안 걸린다" 를 알아낼 수 있다(사소하지만 공짜로 피할 수 있는 누출).
        passwordPolicy.validate(newPassword, member.getLoginId(), member.getNickname());
        member.updatePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.delete(memberId);
        // ⚠ refresh 삭제만으로는 "다른 기기 세션 무효화"가 아니다 — 그 기기의 access 토큰은 만료까지
        // 그대로 통한다(최대 30분). 비밀번호를 바꾸는 이유가 보통 "남이 쓰고 있다" 이므로 여기가 가장 아프다.
        tokenRevocationStore.revokeAll(memberId);
        log.info("Password changed: {}", memberId);
    }

    public void withdraw(UUID memberId, String accessToken) {
        Member member = find(memberId);
        blacklistAccess(accessToken);
        purge(member);
        log.info("Member withdrawn: {}", memberId);
    }

    /**
     * 회원 하나를 <b>흔적까지</b> 지운다 — 본인 탈퇴와 관리자 강제 삭제(B-24)가 <b>공유하는 경로</b>다.
     *
     * <p>따로 두면 한쪽만 정리되는 어긋남이 생긴다("관리자로 지운 회원은 데이터가 남는다" 같은 것) —
     * 그래서 진입점은 둘이어도 실제 삭제는 여기 한 곳이다.
     *
     * <p>순서에 이유가 있다:
     * <ol>
     *   <li><b>토큰 무효화 먼저</b> — 회원 행이 사라진 뒤에도 남의 기기에 있던 access 토큰은 만료까지
     *       통한다(E-2). refresh 삭제 + 발급시각 컷오프로 전부 끊는다.
     *   <li><b>배송지 삭제</b> — 같은 도메인이라 이벤트를 거치지 않고 직접 지운다(개인정보 — F-1 의 핵심).
     *   <li><b>이벤트 발행</b> — 다른 도메인(적립금·찜·쿠폰·알림·재입고·문의)이 자기 것을 지운다.
     *       회원 행을 지우기 <b>전에</b> 발행한다: 리스너가 회원을 다시 읽어야 할 일이 생기면
     *       그때는 이미 없어서 실패하기 때문이다.
     *   <li><b>회원 행 삭제</b>.
     * </ol>
     *
     * <p>전부 <b>한 트랜잭션</b>이다 — 정리 중 실패하면 회원 삭제도 롤백된다(감사 로그와 같은 판단).
     */
    public void purge(Member member) {
        UUID memberId = member.getId();
        refreshTokenStore.delete(memberId);
        tokenRevocationStore.revokeAll(memberId);
        addressCommandService.deleteAllForMember(memberId);
        eventPublisher.publishEvent(new MemberWithdrawnEvent(memberId));
        memberRepository.delete(member);
    }


    private void blacklistAccess(String accessToken) {
        try {
            Claims claims = jwtProvider.parse(accessToken);
            long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
            tokenBlacklist.blacklist(claims.getId(), ttl);
        } catch (Exception ignored) {
            // 이미 만료/무효면 불필요
        }
    }

    /** MemberResponse의 ship* 필드는 주소록의 기본 항목에서 온다(V18 이전엔 member 컬럼이었다). */
    private MemberResponse withDefaultAddress(Member member) {
        return MemberResponse.of(member, addressQueryService.findDefault(member.getId()));
    }

    private Member find(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
