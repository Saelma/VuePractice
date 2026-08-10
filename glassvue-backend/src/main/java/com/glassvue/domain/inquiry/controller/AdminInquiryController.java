package com.glassvue.domain.inquiry.controller;

import com.glassvue.domain.inquiry.dto.AdminInquiryResponse;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import java.util.UUID;

/**
 * 문의 관리 (관리자) — 2026-08-06, 백로그 G-3.
 *
 * <p>이 태그가 생기기 전까지 <b>관리자 문의 API 는 0개</b>였다(실측). 관리자가 문의를 보는 유일한 길이
 * <b>상품 상세의 문의 탭</b>이라, «어느 상품이든 답을 기다리는 문의가 있나» 를 볼 자리가 없었고,
 * 상품과 무관한 일반 문의는 <b>넣어도 답할 경로가 없었다</b>(그래서 G-3 이 이것에 막혀 있었다).
 *
 * <p>⚠ <b>답변 API 는 새로 만들지 않았다</b> — {@code POST /api/inquiries/{id}/answer} 가 이미
 * 상품 경로 밖에 있어 그대로 쓴다. 즉 여기서 더한 것은 <b>«무엇에 답할지 찾는 길»</b> 하나뿐이다.
 */
@Tag(name = "AdminInquiry", description = "문의 관리 (관리자)")
public interface AdminInquiryController {

    @Operation(summary = "문의 목록 (관리자)",
            description = """
                    상품을 **가로질러** 전체 문의를 본다(고객 목록은 상품별이다).

                    `status` 를 안 보내면 **전체**, `WAITING` 이면 미답변만, `ANSWERED` 면 답변된 것만이다.
                    ⚠ 기본값을 **서버가 정하지 않는다** — 화면이 「미답변」 탭으로 열지언정,
                    API 가 그걸 박아 두면 「전체」를 볼 방법이 사라진다.

                    정렬은 `createdAt`·`updatedAt`·`status` 만 받는다(그 밖은 400). 기본은 최신순.

                    🔴 **비밀글도 본문이 그대로 실린다** — 관리자는 원래 열람 대상이고(고객 응답의
                    마스킹 규칙에도 관리자 갈래가 있다), 답을 쓰라면서 질문을 가릴 수는 없다.
                    대신 `secret` 이 실리니 화면에서 「비밀글」로 표시할 것.

                    ⚠ `productName` 은 **조회 시점 값**이고 **null 일 수 있다**(상품이 지워졌거나,
                    상품과 무관한 일반 문의). 그때도 **줄은 남는다** — 목록에서 빠지면 관리자는
                    답할 대상이 있다는 사실 자체를 모른다. 첨부 이미지는 싣지 않는다.
                    """)
    ResponseEntity<ApiResponse<PageResponse<AdminInquiryResponse>>> list(
            InquiryStatus status, Boolean hidden, @ParameterObject Pageable pageable);

    @Operation(summary = "문의 숨김 (관리자)", description = """
            부적절한 문의(욕설·광고 등)를 **숨긴다**. 삭제가 아니라 되돌릴 수 있다 (B-18, 2026-08-10).

            숨기면 **상품 문의 목록**과 **내 문의**에서 빠진다 — ⚠ **작성자 본인에게도** 안 보인다
            (리뷰 숨김과 같은 규칙). 관리자 목록에는 그대로 남는다. 안 남으면 되돌릴 방법이 없다.

            ⚠ **이미 숨겨진 문의에 다시 호출해도 200 이다** — 다만 아무 일도 안 하고 **감사도 안 남긴다**
            (일어나지 않은 조작을 원장에 적지 않는다).

            누가 숨겼는지는 감사 로그에 `INQUIRY_HIDE` 로 남는다 — ⚠ 그 조회는 **`SUPER_ADMIN` 전용**이다.
            """)
    ResponseEntity<ApiResponse<Void>> hide(@Parameter(hidden = true) AuthUser admin, UUID id);

    @Operation(summary = "문의 숨김 해제 (관리자)",
            description = "숨김을 푼다. 다시 상품 문의 목록·내 문의에 나타난다. 감사에 `INQUIRY_UNHIDE` 로 남는다.")
    ResponseEntity<ApiResponse<Void>> unhide(@Parameter(hidden = true) AuthUser admin, UUID id);
}
