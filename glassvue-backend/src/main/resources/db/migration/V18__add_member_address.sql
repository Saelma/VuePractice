-- 배송지 주소록 (2026-07-24, 백로그 B-5)
--
-- 지금까지 배송지는 member 의 ship_* 5컬럼 = **회원당 딱 하나**였다(V11). 집/회사를 구분할 수 없다.
-- 별칭을 붙인 여러 주소를 두고 그중 하나를 기본 배송지로 쓰게 한다.
--
-- ============================================================================
-- ⚠ 이 마이그레이션은 V13~V17 과 성격이 다르다 — **순수 추가가 아니다**
-- ============================================================================
-- 다섯 개는 전부 컬럼·테이블 ADD 였지만 여기엔 **기존 값 이관**이 붙는다.
-- 그래도 이 파일은 **추가만** 한다 — member.ship_* 는 **그대로 남긴다.**
--
-- 왜 여기서 DROP 하지 않나:
--   ddl-auto=validate 이고 운영 jar 의 Member 엔티티가 ship_* 5개를 매핑한다.
--   통합 테스트를 돌리는 순간 Flyway 가 공유 espdb 에 적용되므로(WORKING-AGREEMENTS §5),
--   여기서 DROP 하면 **운영 구 jar 는 재기동 불가**가 되고 회원·주문 조회가 ORA-00904 로 깨진다.
--   7/21 의 닉네임 UNIQUE(V6) 사고와 같은 종류인데, UNIQUE 는 신규 가입만 막았지만
--   컬럼 DROP 은 **기존 조회를 통째로** 깬다.
--
--   → expand / contract 로 나눈다:
--       V18 (지금)  member_address 신설 + 값 복사        ← 순수 추가. 구 jar 무영향
--                   신 코드 배포 (읽기·쓰기는 member_address)
--       V19 (나중)  member.ship_* DROP                    ← 신 코드가 운영에 올라간 뒤
--
--   그 사이 두 곳에 같은 값이 있다(이중 진실). 짧은 기간이라 감수하되,
--   **V18 이후 신 코드는 member.ship_* 에 쓰지 않는다** — 안 그러면 두 값이 갈라져
--   나중에 어느 쪽이 맞는지 판단할 수 없어진다.
--
-- ⚠ orders.ship_* 는 건드리지 않는다. 그건 주문 시점 **스냅샷**이라 주소록과 무관하고,
--    주소록을 고쳐도 과거 주문의 배송지는 그대로여야 한다(V5·V9·V11·V14·V16·V17 과 같은 원칙).

-- ---------------------------------------------------------------- 주소록
--
-- FK 를 **진짜로 건다**. member_coupon.member_id 는 "FK 아님(느슨한 참조)" 이었는데 이건 다르다 —
-- 그건 coupon 도메인이 member 를 가리키는 **도메인 간** 참조라 경계를 지키려고 느슨하게 뒀고,
-- member_address 는 **member 도메인 안**이라 MSA 로 쪼개도 member 와 함께 움직인다.
--
-- ON DELETE CASCADE 인 이유: 회원 탈퇴(MemberService.withdraw)가 member row 를 지운다.
-- CASCADE 가 없으면 주소가 남아 있는 회원은 **탈퇴 자체가 FK 위반으로 실패**한다.
CREATE TABLE member_address (
    id         RAW(16)                     NOT NULL,
    member_id  RAW(16)                     NOT NULL,
    -- 별칭 — 이 기능의 존재 이유다(집/회사를 구분하려고 만들었다).
    alias      VARCHAR2(30 CHAR)           NOT NULL,
    recipient  VARCHAR2(50 CHAR)           NOT NULL,
    phone      VARCHAR2(20 CHAR)           NOT NULL,
    zipcode    VARCHAR2(10 CHAR)           NOT NULL,
    address1   VARCHAR2(200 CHAR)          NOT NULL,
    address2   VARCHAR2(200 CHAR),
    is_default NUMBER(1,0)                 NOT NULL
               CONSTRAINT ck_member_address_default CHECK (is_default IN (0,1)),
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_member_address_member FOREIGN KEY (member_id)
        REFERENCES member (id) ON DELETE CASCADE
);

-- 길이·semantics 는 member.ship_* 와 **완전히 같게** 맞췄다.
-- 원본에서 통과한 값이 복사할 때 ORA-12899 로 터지는 걸 막는다(WORKING-AGREEMENTS §2-2-1,
-- orders.buyer_nickname 이 실제로 그렇게 어긋나 있었다). 전부 CHAR semantics.

-- "내 주소록" 이 유일한 조회 경로다. 기본 배송지가 먼저 오도록 정렬에 쓰인다.
CREATE INDEX idx_member_address_member ON member_address (member_id, is_default);

-- **회원당 기본 배송지는 최대 하나** — 앱 로직이 아니라 DB 가 보장한다.
-- Oracle 에는 부분 유니크 인덱스가 없으므로 함수 기반 유니크 인덱스로 같은 효과를 낸다:
-- is_default=1 인 행만 member_id 가 색인되고(나머지는 NULL 이라 유니크 대상에서 빠진다),
-- 같은 회원의 두 번째 기본 배송지는 ORA-00001 로 거부된다.
--
-- 앱이 "옛 기본 해제 → 새 기본 지정" 순서를 지키는지 확신할 수 없어서 DB 에 남긴다 —
-- 주문번호 유니크(V15)를 "앱 채번이 어긋났을 때의 최종 방어선" 으로 둔 것과 같은 성격이다.
CREATE UNIQUE INDEX uq_member_address_default
    ON member_address (CASE WHEN is_default = 1 THEN member_id END);

-- ---------------------------------------------------------------- 기존 값 이관
--
-- member.ship_* 에 값이 있는 회원은 그 주소를 주소록 첫 항목(기본 배송지)으로 옮긴다.
-- 이게 없으면 기존 회원은 주소록이 빈 채로 시작해 **저장해 둔 배송지가 사라진 것처럼 보인다.**
-- (V4 백필을 넣은 이유와 같다 — 이벤트/입력이 새로 일어나기 전까지 영영 비어 있게 된다.)
--
-- ⚠ PK 를 SQL 에서 만들어야 한다. CLAUDE.md 는 PK 를 **UUIDv7 · 앱에서 생성**으로 못박고 있는데
--    백필 행은 앱을 거치지 않는다. SYS_GUID() 는 v7 이 아니므로(시간순이 아니다) 규칙 위반이고,
--    한 번 들어가면 나중에 구분할 방법도 없다. 그래서 **v7 레이아웃을 그대로 조립**한다:
--
--      [48비트 unix ms = 12 hex] [버전 '7'] [rand_a 3 hex] [variant 'A'] [rand_b 15 hex]  = 32 hex
--
--    RFC 9562 의 variant 는 상위 2비트가 10 이어야 하므로 17번째 hex 는 8·9·A·B 중 하나여야 한다 → 'A' 고정.
--    난수부는 SYS_GUID() 에서 잘라 쓴다(이 플랫폼의 SYS_GUID 는 순차적이라 무작위성은 약하지만,
--    필요한 건 **서로 다른 값** 뿐이고 PK 유니크가 최종 방어선이다).
--    실측: 생성값 019F9175E3107575... → 13번째 hex 가 '7', 앞 12 hex 를 되읽으면 현재 UTC 와 일치.
--
-- 대상은 recipient·address1 이 **둘 다** 있는 회원만이다. 화면 검증(@NotBlank)상 부분 입력은
-- 생길 수 없지만, 한쪽만 있는 행을 NOT NULL 컬럼에 넣으면 마이그레이션이 통째로 실패한다.
--
-- 빈 DB(esptest)에서는 대상이 0건이라 아무 일도 일어나지 않는다.
INSERT INTO member_address (
    id, member_id, alias, recipient, phone, zipcode, address1, address2, is_default,
    created_at, updated_at
)
SELECT
    HEXTORAW(
        LPAD(TO_CHAR(ROUND((CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS DATE) - DATE '1970-01-01')
                           * 86400000), 'FMXXXXXXXXXXXX'), 12, '0')
        || '7' || SUBSTR(RAWTOHEX(SYS_GUID()), 1, 3)
        || 'A' || SUBSTR(RAWTOHEX(SYS_GUID()), 4, 15)
    ),
    m.id,
    '기본 배송지',          -- 별칭은 그때 안 받았으므로 지어낼 수 없다. 사용자가 화면에서 고치면 된다.
    m.ship_recipient,
    -- phone·zipcode 는 새 테이블에서 NOT NULL 인데 member 에서는 nullable 이었다.
    -- 실제로 NULL 인 행은 없지만(화면이 @NotBlank 로 막았다), 마이그레이션이 데이터에 기대면 안 된다.
    NVL(m.ship_phone, '-'),
    NVL(m.ship_zipcode, '-'),
    m.ship_address1,
    m.ship_address2,
    1,                      -- 옮겨온 유일한 주소이므로 기본 배송지
    SYSTIMESTAMP,
    SYSTIMESTAMP
FROM member m
WHERE m.ship_recipient IS NOT NULL
  AND m.ship_address1  IS NOT NULL;
