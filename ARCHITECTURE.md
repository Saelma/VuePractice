# 아키텍처

이 문서는 **설계 배경·ERD·미래 로드맵**을 담는다.
매 세션 지켜야 할 짧은 규칙은 [`CLAUDE.md`](./CLAUDE.md)에, 실제 스키마는 JPA 엔티티(코드)에 있다.

- **현재 단계**: 모노레포 · 게시판(notice) 연습
- **최종 목표**: 이커머스로 확장, 필요 시 MSA 전환
- **핵심 원칙**: *바꾸기 비싼 것(구조·공통 규칙)만 지금 고정하고, 도메인 테이블은 그 단계에서 만든다.*

---

## 1. 원칙

### 도메인 단위 패키지 (레이어 단위 ❌)

MSA 전환 시 **폴더째 들어내면 하나의 서비스**가 되도록 도메인으로 나눈다.
`controller/`·`service/`·`repository/`를 최상위에 두는 레이어 구조는, 서비스를 쪼갤 때 세 폴더를 헤집어야 해서 금지.

### 미리 만들지 않는다

이커머스 상품/주문 테이블을 지금 그리면 요구사항이 없어 십중팔구 틀린다.
게시판을 억지로 재사용하려는 **만능 board 테이블 + type 구분 + nullable FK(polymorphic association)** 도 금지.
상품문의·리뷰는 그때 catalog 도메인에 **별도 테이블**로 만든다.

> 진짜 "미리 준비"는 테이블 재사용이 아니라 **패턴 재사용**이다.
> 게시판에서 익힌 CRUD·페이징·QueryDSL 동적검색·Redis 조회수/캐시가 상품 목록·필터·리뷰로 그대로 이식된다.

⚠ **이 원칙은 "운영에 필요한가" 한 축이다.** 이 프로젝트는 **학습이 목적이고 운영하지 않으므로**,
*배우려고* 넣는 것은 별도 축에서 판단한다 — **§6 「학습 트랙」**. 이미 그 예외를 의식적으로 쓴 적이 있다
(C-8 상품 옵션 — 재고 구조 대공사를 실제로 겪어 보려고 도입, BACKLOG 「완료」 주석).
단 학습 트랙에도 통과 조건이 있다: **겪을 거리가 실재해야 한다**(볼 게 없는 도구 설치 = no-op).

---

## 2. 패키지 구조

최상위를 **`domain/`(업무) + `global/`(공통·인프라)** 로 나눈다.

```
com.glassvue
├─ domain/                     업무 도메인 — MSA 분리 단위
│  ├─ notice/                  도메인 1 — 게시판 (현재)
│  │  ├─ entity/               Notice
│  │  ├─ repository/           NoticeRepository (+ Custom/Impl = QueryDSL)
│  │  ├─ service/
│  │  │  ├─ command/           NoticeCommandService  (등록·수정·삭제)
│  │  │  └─ query/             NoticeQueryService    (목록·조회·검색, readOnly)
│  │  ├─ controller/           NoticeController(interface, Swagger) + NoticeControllerImpl
│  │  └─ dto/                  record (요청/응답)
│  └─ (로드맵) member/  coupon/  project/  catalog/  order/  review/ ...
│
└─ global/                     공통 · 인프라 (도메인 아님)
   ├─ common/                  BaseTimeEntity (@MappedSuperclass, UUIDv7 @Id, createdAt/updatedAt)
   ├─ response/                ApiResponse, PageResponse
   ├─ exception/               ErrorCode(enum), BusinessException, GlobalExceptionHandler
   ├─ config/                  JpaAuditing, Swagger, Querydsl
   └─ (로드맵) security/  redis/  messaging/  log/
```

- `domain/<x>` 하위는 MSA 전환 시 그 폴더째 독립 서비스로 들어낸다.
- `global/messaging/` — 이벤트 큐 자리. **RabbitMQ 확정**(Kafka 아님)이라 구현체 이름 대신 `messaging`으로 둔다.
- `global/redis/` · `global/security/` · `global/log/` — 각각 조회수/캐시, 로그인, 관측 단계에서 채운다.

---

## 3. 공통 규칙 상세

| 항목 | 규칙 | 이유 |
|---|---|---|
| PK | **UUIDv7** + Oracle **RAW(16)** (앱에서 생성) | 아래 "PK — UUIDv7" 참조. Long/SEQUENCE/IDENTITY·문자열 저장 금지 |
| 감사 컬럼 | `BaseTimeEntity`(`@MappedSuperclass` + **Spring Data JPA Auditing**)에 `createdAt`/`updatedAt`, 타입 **`Instant`(UTC)**, 전 엔티티 상속 | 로그인 단계에 `BaseEntity extends BaseTimeEntity`로 작성자(`@CreatedBy`/`@LastModifiedBy`)를 얹는다. Hibernate `@CreationTimestamp` 대신 Spring Data Auditing을 쓰는 이유 = 작성자까지 같은 방식으로 처리 |
| 네이밍 | 테이블·컬럼 snake_case, 단수형 | 일관성 |
| Oracle 예약어 | `ORDER`·`DATE`·`NUMBER` 등 회피 → 주문 테이블은 `orders` | 예약어 충돌 방지 |
| 응답 포맷 | `ApiResponse`로 통일 (성공/에러 공통 래퍼) | 프론트·문서 일관성 |
| 비밀값 | `.env` 환경변수로만 주입 (코드·properties에 두지 않음) | 이미 적용됨 |

### 코드 컨벤션

| 항목 | 규칙 |
|---|---|
| 의존성 주입 | **생성자 주입만** (`@RequiredArgsConstructor` + `private final`). 필드 주입 금지 |
| 컨트롤러 | 인터페이스 `XxxController`(Swagger 애노테이션) + 구현 `XxxControllerImpl`로 분리 |
| 서비스 | 경량 CQRS — `XxxCommandService`(조작) / `XxxQueryService`(조회, `@Transactional(readOnly=true)`) |
| DTO | Java `record` (요청 DTO 검증 애노테이션은 record 컴포넌트에) |
| 로깅 | SLF4J `@Slf4j`. `System.out`·`printStackTrace` 금지 |
| 쿼리 작성 | **동적이면 QueryDSL, 고정이면 JPQL `@Query`** (아래 참조) |

> **CQRS 범위**: 여기서의 CQRS는 *서비스 계층을 command/query로 분리*하는 경량(application-level) CQRS다.
> 읽기/쓰기 모델을 물리적으로 분리하고 이벤트로 동기화하는 풀 CQRS는 트래픽이 커지는 이커머스 단계의 선택지로 남겨둔다.
> 조회 측은 QueryDSL로 엔티티를 거치지 않고 DTO를 바로 projection 한다.

### 쿼리 작성 기준 — QueryDSL vs JPQL

판단 기준은 **조건이 런타임에 바뀌는가** 하나다. 취향이나 최신성이 아니다.

| | 쓰는 곳 | 사례 |
|---|---|---|
| **QueryDSL** (`XxxRepositoryCustom` + `XxxRepositoryImpl`) | **동적 쿼리** — 검색어·필터·정렬·페이징처럼 조건 조합이 런타임에 결정 | `ProductRepositoryImpl`, `NoticeRepositoryImpl`, `ReviewRepositoryImpl`, `InquiryRepositoryImpl`. 공용 지원은 `global/querydsl`(`ConditionBuilder`·`SortSupport`·`QueryDslSupport`) |
| **JPQL `@Query`** (리포지토리 인터페이스에 직접) | **고정 쿼리** — 조건이 컴파일 타임에 확정 | `decreaseStock`·`increaseStock`(벌크 UPDATE), `findStockSnapshot`, `statsByProduct`, `existsPurchase` |

고정 쿼리에 QueryDSL을 쓰면 `Custom` 인터페이스 + `Impl` 구현까지 파일 3개를 늘리면서 얻는 게 없다. 반대로 동적 조건이 하나라도 생기면 JPQL 문자열 조립으로 버티지 말고 QueryDSL로 옮긴다.

> **JPQL 생성자 표현식의 알려진 약점**: `select new com.…StockSnapshot(...)`처럼 클래스 경로가 **문자열**이라, DTO를 옮기거나 이름을 바꾸면 컴파일은 통과하고 런타임에 터진다. QueryDSL의 `Projections.constructor`는 이게 컴파일 타임에 잡힌다. 고정 쿼리라 JPQL을 유지하되, **프로젝션 DTO 이동·개명 시 JPQL 문자열을 같이 고쳤는지 반드시 확인**할 것.

#### 벌크 UPDATE 직후의 값은 엔티티로 읽지 않는다 (2026-07-20)

`@Modifying` 벌크 JPQL UPDATE는 **DB만 고치고 영속성 컨텍스트(1차 캐시)는 건드리지 않는다.** 같은 트랜잭션에서 이미 로딩된 엔티티가 있으면 `findById`는 DB에 가지 않고 **차감 전 값(stale)** 을 돌려준다.

- 실제 사례: `checkout`은 `@Transactional`이고 `cartService.getCart` → `findByIds`가 `Product`를 이미 로딩한다. 여기서 `decreaseStock` 후 `findById`로 재고를 읽으면 차감 전 값이 나와 재고 부족 알림이 안 나간다.
- 해법: **스칼라 프로젝션**(`StockSnapshot` — 엔티티가 아니라 1차 캐시를 우회해 DB를 직접 읽음).
- `@Modifying(clearAutomatically = true)`는 **쓰지 않는다** — 영속성 컨텍스트 *전체*를 비워서 같은 트랜잭션의 다른 엔티티(Order·Cart 등) 더티 체킹까지 날아간다.

### 예외 처리 · 응답 포맷

- 모든 응답은 공통 래퍼 `ApiResponse<T>`.
  - 성공: `{ "success": true, "data": ... }`
  - 에러: `{ "success": false, "error": { "code": "...", "message": "..." } }`
  - **HTTP 상태코드는 그대로 유지** (에러도 4xx/5xx, 200에 담지 않는다).
- 에러 정의는 `ErrorCode` **enum 카탈로그**에 모은다 (코드 · HttpStatus · 메시지 한곳).
- 도메인에선 `throw new BusinessException(ErrorCode)`. 컨트롤러/서비스에서 try-catch로 응답을 만들지 않는다.
- `@RestControllerAdvice` **전역 핸들러** 한 곳에서 `BusinessException` · 검증 실패(`MethodArgumentNotValidException`) · 미처리 예외를 `ApiResponse` 에러로 변환.
- 목록은 `PageResponse`(content · page · size · totalElements)를 `data`에 담는다. DevExtreme DataGrid에는 프론트에서 매핑.

> 참고: 에러를 곧장 반환하는 RFC 9457 ProblemDetail 방식도 있으나, 프론트가 한 포맷만 보도록 공통 래퍼로 통일했다. (ErrorCode 보일러플레이트는 에이전트가 작성)

### 접근 제어 (도메인 경계)

- **cross-domain 직접 참조 금지** — 다른 도메인의 내부 클래스를 직접 호출하지 않는다. 도메인 간 통신은 공개 인터페이스나 이벤트(`ApplicationEventPublisher`)로만. MSA 분리의 전제.
- 도메인 내부 구현체(`~ControllerImpl`, 서비스 · repository 구현)는 가시성을 최소화한다.
- 서브패키지(entity/service/controller) 구조라 순수 package-private로는 경계를 강제할 수 없다 → **지금은 규칙으로 지키고**, 필요해지면 **Spring Modulith**로 테스트에서 경계를 검증한다(로드맵).

### PK — UUIDv7 (RAW(16))

PK 타입은 나중에 바꾸기가 가장 비싼(전 FK·인덱스·API 변경) 결정이라 **지금 UUID로 확정**한다.

- **왜 UUID**: 서비스가 중앙 시퀀스 없이 각자 ID 생성 → MSA 친화적. ID로 업무정보(순번) 노출 안 됨.
- **왜 v4가 아니라 v7**: v4는 완전 랜덤이라 PK로 쓰면 인덱스 조각화·페이지 분할로 insert가 느려진다. **v7은 앞부분이 시간(ms)** 이라 시간순으로 쌓여 인덱스가 auto-increment처럼 조밀하게 유지된다 (RFC 9562).
- **크기**: v7도 128비트(16바이트) — BIGINT(8)의 2배. "더 작은 UUID"는 없다. 대신 Oracle에 **문자열(VARCHAR2 36) 아닌 RAW(16) 바이너리**로 저장해 절반 이하로 줄인다.
- **생성**: Java 기본 `UUID`·Hibernate `@UuidGenerator(style=TIME)`는 엄밀한 RFC v7이 아니므로 **`uuid-creator` 라이브러리**(`UuidCreator.getTimeOrderedEpoch()`)로 앱에서 생성. `BaseTimeEntity`에 `@Id UUID id`를 두어 전 엔티티가 상속.
- **매핑 확인**: Hibernate가 `UUID`를 Oracle RAW(16)으로 매핑하는지 DDL로 검증할 것(구현 시 to-do).
- **참고**: "작으면서 시간순·분산"이 목적이면 UUID가 아니라 Snowflake ID(64비트 BIGINT)가 답. 지금은 UUID 노선으로 확정.

---

## 4. 현재 도메인 — notice (게시판)

```
notice
─────────────────────────────────────────
id          RAW(16)     PK (UUIDv7, 앱에서 생성)
title       VARCHAR2    제목
content     CLOB        본문
author      VARCHAR2    작성자명 (로그인 전엔 문자열, 3단계에 member FK로 전환)
view_count  NUMBER      조회수 (평소 Redis, 주기적으로 반영)
pinned      NUMBER(1)   상단 고정 여부 (선택)
created_at  TIMESTAMP   ← BaseTimeEntity
updated_at  TIMESTAMP   ← BaseTimeEntity
```

- 댓글·첨부는 다음 단계에 `comment`·`attachment` 별도 테이블로.
- 조회수는 Redis 카운터로 처리하고 주기적으로 `view_count`에 반영한다.

### 게시판 진행 계획

1. 엔티티 · 목록 API · Swagger(springdoc)
2. Vue + DevExtreme DataGrid 연동
3. QueryDSL 동적 검색 (제목 · 작성자 · 기간)
4. Redis — 조회수 카운터 · 목록 캐시
5. 로그인 (Spring Session + Redis) → 이때 `member` 도메인 등장

---

## 5. 도메인 로드맵 (이커머스)

테이블이 아니라 **도메인 순서**로만 잡는다. 실제 스키마는 각 단계에서 확정.

```
member          회원 · 인증 (게시판 5단계 로그인이 시작점)          ✅
  └▶ catalog    상품 · 카테고리 · **옵션(variant)**   (재고가 옵션마다)   ✅
       ├▶ wishlist 찜            (테이블 — 로그아웃해도 남는다)      ✅ 2026-07-24
       ├▶ restock  재입고 알림 신청 (상품 총재고 0→양수에 반응)        ✅ 2026-07-27
       ├▶ point    적립금·회원등급  (배송완료 적립, 이력이 원장)       ✅ 2026-07-24
       └▶ cart  장바구니          (Redis 활용)                      ✅
            └▶ orders / order_item   주문 · 결제상태(PAID/SHIPPED)   ✅ 2026-07-16
                 └▶ review / inquiry  상품 리뷰 · 문의 (게시판 경험 재활용)  ✅ 2026-07-16
                 └▶ 반품(return)  배송완료 반품 → 적립금 환불  ✅ 2026-07-24 (실결제 PG 는 아직)
                 └▶ (로드맵) payment  실제 결제(PG 연동)  ⏸ MSA 단계 + PG사 확정 시

audit           관리자 조작 감사 이력 (append-only, 이벤트로만 유입 — 어느 도메인에도 종속 안 됨)  ✅ 2026-07-28
```

> **review / inquiry (2026-07-16 구현)**: 상품에 **느슨한 UUID 참조**(product_id)로 연결, polymorphic FK 없이 별도 테이블.
> 리뷰는 **구매자만**(order 도메인 `hasPurchased` 공개 API로 인증)·상품당 1회·평균별점 집계. 문의는 **비밀글 마스킹**(응답 DTO)·**ADMIN 답변**·상태(WAITING/ANSWERED).
>
> **2026-07-20 마감**: ①구매 인증 범위를 `ORDERED`만 → `ORDERED·PAID·SHIPPED`로 수정(**CANCELLED만 제외**).
> 주문 상태 확장(7/16) 때 누락돼 결제·배송 완료 고객이 리뷰를 못 쓰던 버그. 새 상태가 자동으로 포함되지
> 않도록 `<> CANCELLED`가 아닌 **명시적 열거**로 둔다. ②**포토 리뷰** — `review.image_group_id`로
> ImageGroup 재사용 구조의 두 번째 사용처(FK 없는 느슨한 UUID, `ImageService` 공개 API로만 접근).
> ③상품 목록 평균별점은 **비정규화 + 이벤트 동기화**(§6.0 이벤트 항목 참조).
>
> **✅ 2026-07-31 (B-15) 문의 답변 알림** — 문의는 **읽기 전용 종착점이 아니다.** 그동안
> `answer()` 는 상태만 바꾸고 아무에게도 알리지 않아, **물어본 사람이 답을 못 보는** 구조였다
> (문의 화면에 다시 들어와야 안다). `InquiryAnsweredEvent` → `InquiryEventListener` →
> `InquiryNotificationHandler` 3층으로 붙였다. 두 가지가 이 자리 고유의 것이다:
> - ⚠ **"첫 답변에서만" 발행한다.** 같은 API 가 등록·수정 겸용이라 무조건 발행하면 관리자가 오타를
>   고칠 때마다 알림이 간다. 판정(`!inquiry.isAnswered()`)은 반드시 `inquiry.answer()` **앞**에서 —
>   뒤에서 읽으면 이미 `ANSWERED` 라 영영 `false` 가 되어 **알림이 아예 안 나간다**(둘 다 컴파일되고
>   답변 기능은 멀쩡하다. 이 한 줄 위치가 기능의 전부라 테스트 3건으로 못박았다).
> - ⚠ **문의는 자기 URL 이 없다** — 상품 상세 안에 붙어 있다. 알림 링크는 `/products/{id}#inquiries`
>   이고, 앵커 처리는 라우터 `scrollBehavior` 가 **아니라** `ProductDetailView` 안에서 한다
>   (라우팅 시점엔 상품을 아직 못 받아 문의 섹션이 렌더 전이라 앵커 요소가 없다).
>
> 알림 타입은 `NotificationType.INQUIRY` 추가만으로 끝났다 — `notification.type`·
> `member_notification_pref.type` 에 **CHECK 제약이 없어**(V26 실측) 마이그레이션이 없고,
> 설정 화면은 서버가 `NotificationType.values()` 를 통째로 내려주므로 **토글이 저절로 생긴다**.

### 이미지 생명주기 규칙 (2026-07-20)

업로드가 **2단계**(①`POST /api/images`로 올려 id를 받고 ②저장 시 `imageIds`로 전달)라
①만 하고 ②를 안 하면 주인 없는 이미지가 남는다. 정리 책임을 이렇게 나눈다.

| 고아 종류 | 누가 치우나 | 이유 |
|---|---|---|
| 업로드만 하고 미사용 (`image_group_id IS NULL`) | image 도메인 **스위퍼**(`@Scheduled`, 유예 24h) | 언제 저장할지 알 수 없으니 시간 기준으로만 판단 가능 |
| 교체·삭제로 버려진 그룹 | **소유 도메인**이 `ImageService.deleteGroup` 호출 | 버려졌는지 알려면 catalog·review 참조를 봐야 하는데, 그건 `image → 타 도메인` **역방향 의존**이라 경계를 깬다. 교체하는 쪽은 옛 그룹 id를 이미 안다 |

- **호출 순서**: `deleteGroup`은 반드시 `createGroup` **뒤에**. createGroup이 유지할 이미지를 새 그룹으로
  재할당하므로, 그 뒤 옛 그룹에 남는 건 사용자가 *제거한* 이미지뿐이다. 순서를 바꾸면 유지할 이미지까지 지워진다.
- **파일 삭제는 `AFTER_COMMIT`**(`ImageFilesReleasedEvent`). 트랜잭션 안에서 지우면 롤백 시
  DB row는 살아나는데 파일은 사라져 **깨진 이미지**가 된다. 되돌릴 수 없는 쪽을 나중에 둔다.
- **통합 테스트가 업로드를 하면 파일을 직접 치울 것** — `@Transactional`은 DB만 롤백하고
  파일 쓰기는 되돌리지 않는다. DB row가 없어 스위퍼도 못 잡는다.
> 도메인 간 통신은 공개 서비스로만(catalog `ProductQueryService.ensureExists`, order `OrderService.hasPurchased`).

> **배송비(2026-07-23, V14)**: `orders.total_price` 는 **상품 합계**(배송비 제외)이고, 배송비는
> `orders.shipping_fee` 에 **따로** 둔다. `total_price` 에 더해버리면 이미 쌓인 주문의 숫자가
> "상품 합계"인지 "결제 금액"인지 알 수 없어진다. 결제 금액(`payAmount`)은 **저장하지 않고 계산**한다
> — 저장하면 두 값이 어긋날 여지가 생긴다.
> **정책은 설정(`glassvue.shipping`), 부과된 금액은 스냅샷** — 정책이 바뀌어도 과거 주문의 배송비는
> 그대로여야 한다(배송지·구매자 닉네임과 같은 이유). 배송비는 **서버가 계산**한다(요청 본문으로 받으면
> 0원으로 위조 가능 — 품목·가격을 장바구니에서 읽는 것과 같은 이유).
> `ShippingPolicy` 는 **global** 에 있다: 장바구니(주문 전 미리보기)와 주문(부과)이 둘 다 읽어야 하는데
> 이미 `order → cart` 의존이 있어 order 에 두면 `cart → order` 로 **순환**이 된다.
>
> **적립금 · 회원 등급 (2026-07-24, V21)**: `point_account`(잔액·누적구매·등급) + `point_history`(원장) +
> `orders.used_point·earned_point`(스냅샷). 잔액·등급을 `member` 가 아니라 point 도메인이 갖는다
> (coupon 이 member_coupon 을 갖는 것과 같은 경계).
>
> ⚠ **적립 시점은 배송완료.** 취소는 `ORDERED`·`PAID` 에서만 되므로 `DELIVERED` 적립은 **되돌릴 일이 없다**
> — 회수 로직이 아예 불필요하다("규율이 아니라 구조로 막는다"의 또 다른 적용).
>
> ⚠ **적립을 이벤트로 빼지 않는다.** 리스너가 `@Async`(인프로세스 best-effort)라 유실 가능한데,
> 알림 유실은 감수해도 **적립 유실은 고객 돈이 사라진다.** `deliver()` 안에서 동기로 끝내고,
> `OrderDeliveredEvent` 는 결과 알림용이다. **재고 복원을 이벤트로 안 뺀 것과 같은 판단.**
> 유실 금지를 이벤트로 보장하려면 아웃박스/RabbitMQ(MSA 단계).
>
> **이력이 원장, 잔액은 캐시.** 잔액을 바꾸는 경로가 항상 이력을 함께 남기도록 묶었다
> (통합테스트가 `SUM(amount)==balance` 로 대조). `amount` 는 부호 있는 값(적립 +, 사용 −).
>
> **적립 기준액 = 상품합계 − 쿠폰할인 − 사용 적립금**(배송비 제외). 적립금으로 낸 부분을 빼는 이유는
> **포인트가 포인트를 낳지 않게** 하기 위해서다. 적립률은 **누적 반영 후 등급**으로 계산한다(승급 즉시 적용).
> 금액 순서: 상품합계 → 쿠폰 → 적립금 → 배송비. 배송비는 여전히 할인 전 상품합계 기준.
>
> ⚠ **적립은 주문의 부가 결과지 전제 조건이 아니다.** 계정 없는 회원의 배송완료가 `POINT-404` 로 막히던
> 걸 통합테스트가 잡았다 — 쓰기 경로에서는 계정을 그 자리에서 연다(읽기 경로는 저장 안 함).
>
> ✅ **2026-07-31 (H-2)**: 이 도메인은 소스 17개에 **테스트가 통합 1개**뿐이었다. 게다가 `OrderServiceTest` 는
> `PointService` 를 `@Mock` 으로 바꿔치기해 **한 줄도 실행하지 않는다** — 즉 실질 커버는 한 갈래였다.
> 단위 **53건**을 세 층으로 깔았다: `MemberGradeTest`(등급 임계 99,999↔100,000 · 적립 **내림**) ·
> `PointAccountTest`(잔액이 음수가 되지 않는 마지막 방어선 · 누적/등급이 한 메서드에서 함께 움직임) ·
> `PointServiceTest`(이력의 종류·**부호**·`balanceAfter` · 승급 직후 요율 · 실패 경로에서 이력 0줄).
> ⚠ **통합이 못 보던 자리가 여기다** — 통합은 `SUM(amount)==balance` 로 대조하므로 **사용을 양수로 적어도**
> 합계 검증만으로는 드러나지 않는다. 테스트가 실제로 잡는지는 **운영 코드를 5가지로 일부러 망가뜨려**
> 확인했다(`>=`→`>`, 승급 전 요율, USE 부호 제거, `Math.max` 제거, 내림→반올림 — 전부 실패로 잡힘).
>
> > **반품 · 적립금 환불 (2026-07-24, V24)**: 배송완료 주문을 고객이 반품 요청 → 관리자 승인 →
> 옵션 재고 복원 + **결제금액을 적립금으로 환불**. PG(실결제)가 없어 현금 환불은 못 하지만
> 적립금(V21)이 환불 수단이 됐다. 상태: `DELIVERED → RETURN_REQUESTED → RETURNED`(승인) / `→ DELIVERED`(거절).
>
> **환불 규칙** — 환불액 = 상품합계 − 쿠폰(배송비 제외, 운임은 소진). 배송완료 때 준 적립은 **회수**한다.
> ⚠ **회수가 없으면 포인트 파밍 구멍**이 생긴다(사서 적립받고 반품해서 적립+환불 둘 다 챙김).
> 순변동 = 환불 − 적립 ≥ 0 이 항상 성립(환불이 적립보다 크다) → 잔액이 음수가 될 일이 없고 파밍도 불가능하다.
> 등급 기준(누적 구매확정액)에서도 그 주문 몫을 빼고 재산정한다(강등 가능) — "샀다 반품하면 등급만 남는" 것도 막는다.
> 환불·적립회수·등급 재산정·재고 복원이 **한 트랜잭션**에서 함께 움직인다. 이력은 REFUND 한 줄(순변동), SUM==balance 유지.
>
> ⚠ **교환은 뺐다** — "반품 + 새 주문" 이라 금액 정산이 복잡하다(사용자 결정). 반품만.
>
> > **상품 옵션 / 재고 구조 (2026-07-24, V22·V23)**: "상품 1 = 재고 1" 을 **"상품 1 = 옵션 N, 재고는 옵션마다"** 로
> 바꿨다(백로그 C-8). 재고가 `product.stock` 에서 `product_variant.stock` 으로 내려가면서
> **장바구니(Redis field=variantId)·주문(order_item.variant_id)·재고 차감/복원이 전부 옵션 단위**가 됐다.
>
> 모델은 **단일 옵션 목록**이다 — 옵션그룹(사이즈·색상)을 따로 두지 않고 구매 가능한 조합을 평판화해
> 나열한다("검정 / M" 이 한 줄, 실제 시스템의 SKU 방식). 조합 폭발이 없다. 가격차(`price_delta`)를 두어
> **실제 판매가 = product.price + price_delta** — 기본가를 바꾸면 옵션이 함께 따라온다.
>
> ⚠ **모든 상품이 옵션을 최소 1개 갖는다.** 과자처럼 옵션 없는 상품도 "기본" 한 줄 → 재고가 **항상 옵션에**
> 있어 "옵션 있는/없는 상품" 을 코드가 갈라 처리할 필요가 없다(구조로 막기, V18 과 같은 방식).
> 화면은 옵션이 **2개 이상일 때만** 선택 UI 를 보여주고, order_item 은 단일 옵션이면 `variant_name` 을
> null 로 스냅샷해 "기본" 노이즈를 안 남긴다.
>
> ⚠ **product.stock 은 남기고 엔티티 매핑만 걷어냈다**(expand/contract). 컬럼 DROP 은 후속 버전.
> 다만 `product.stock` 은 **NOT NULL(기본값 없음)** 이라, 매핑을 걷어낸 신 코드의 INSERT 가 그대로면
> ORA-01400 이 난다(V18 의 nullable `ship_*` 와 달랐다) → **V23 으로 DEFAULT 0**. 통합테스트가 잡았다.
>
> **order_item.variant_id 는 취소 시 재고 복원 대상**이다. 옛 주문(옵션 이전)은 V22 가 각 상품의 기본 옵션으로
> 백필했다 — 안 하면 옛 주문 취소가 "어느 옵션에 복원하지?" 에 답이 없다. 삭제된 상품의 옛 order_item 은
> variant_id 가 null 로 남고, 그 취소는 복원을 조용히 건너뛴다(복원할 옵션이 없다).
>
> > **관리자 매출 통계 (2026-07-24, 마이그레이션 없음)**: 기능의 전부가 **"무엇을 매출로 보는가"** 다.
> ①**시각은 `paid_at`**(주문 시점이 아니다 — 영영 결제 안 될 수도 있다),
> ②**상태는 `PAID·SHIPPED·DELIVERED` 명시적 열거**(`<> CANCELLED` 로 쓰면 나중에 추가되는 상태가
> 자동으로 매출에 섞인다 — `existsPurchase` 와 같은 판단. ⚠ **`paid_at` 유무로 거르면 안 된다** —
> 결제 후 취소된 주문은 `paid_at` 이 남아 있지만 환불이다),
> ③**일자는 KST**(`paid_at` 은 UTC 라 그냥 자르면 한국시간 00~09시 결제가 전날로 찍힌다 — V15 와 같은 함정).
> **JPQL 에 타임존 변환이 없어 네이티브 쿼리**를 쓴다.
>
> **상품매출(상품합계−쿠폰할인)과 배송비를 합치지 않는다.** 배송비는 그대로 택배비로 나가는 돈이라
> 섞으면 장사가 잘되는지 읽을 수 없다. 상품별 판매액은 **쿠폰 할인 전**이다 — 쿠폰은 주문 단위라
> 어느 상품이 얼마를 깎았는지 나눌 근거가 없고, 안분하면 그럴듯하지만 **지어낸 숫자**가 된다.
>
> ⚠ **단일 행 집계를 `Object[]` 로 선언하면 안 된다** — Spring Data 가 "한 행의 여러 컬럼"과
> "여러 행"을 구분하지 못해 **조용히 0 을 돌려준다**(2026-07-24 실제 사고: SQL 은 6건/60,000 인데
> API 는 0). `List<Object[]>` 로 받고 첫 행을 꺼낸다.
>
> 이 기능은 **마이그레이션이 없다** — 기존 `orders`·`order_item` 만으로 다 나왔다.
> 스냅샷(구매자·상품명·가격·배송비·쿠폰)을 꼬박꼬박 남겨 둔 것이 여기서 값을 했고,
> 특히 `order_item.product_name` 덕에 **상품별 집계가 catalog 를 조회하지 않는다**(도메인 의존 없음).
> 상품명이 바뀌면 `product_id` 로 묶고 **가장 최근 결제 건의 이름**을 쓴다(`KEEP DENSE_RANK LAST`).
>
> **위시리스트(찜) (2026-07-24, V19)**: `wishlist` 테이블(회원·상품 한 쌍). 장바구니와 구조가 거의
> 같지만 **저장소가 다르다** — 장바구니는 Redis(세션 수명이면 충분), 찜은 **로그아웃해도 남아야** 하므로
> 테이블이다. Redis 에 두면 TTL·플러시·재시작에 조용히 날아가고 사용자는 "찜한 게 사라졌다"로만 겪는다.
>
> ⚠ **"내가 이 상품을 찜했나"를 `ProductResponse` 에 넣지 않는다.** 그러면 catalog 가 wishlist 를
> 알아야 해서 **도메인 순환**이 된다(`wishlist → catalog` 가 이미 있다). 2026-07-20 에 상품 목록 별점을
> 이벤트+비정규화로 우회한 것과 **같은 문제**인데, 찜은 **회원마다 다른 값**이라 비정규화할 수도 없다.
> → **"내가 찜한 상품 id 집합"을 주는 API**(`GET /api/wishlist/product-ids`)를 따로 두고 **화면이 합친다.**
> 도메인 순환을 피하는 세 번째 수단인 셈이다(이벤트·비정규화·클라이언트 조인).
>
> **재입고 알림 (2026-07-27, V28)**: `restock_subscription` 테이블(회원·상품 한 쌍) — 위시리스트와 같은 모양.
> 품절 상품에 "재입고되면 알림 받기"를 저장하고, 상품이 다시 들어오면 신청자에게 인앱 알림(V26)을 보낸 뒤
> 그 상품 구독을 **소진**한다(일회성). 버튼 상태도 찜과 같은 방식(`GET /api/restock/product-ids` + 화면 합성)이라
> 도메인 순환을 피한다.
> ⚠ **단위가 옵션이 아니라 상품**이다. 관리자 상품 편집이 옵션(`product_variant`)을 delete+재삽입하며
> `variant.id` 가 매번 새로 생겨, 옵션 id 로 구독을 걸면 편집 한 번에 전부 고아가 된다. 그래서 (member, product)
> 로 잡고 재입고 판정도 **상품 총재고 0→양수**로 한다(`StockReplenishedEvent`). 3층 이벤트 중 **핸들러를
> restock 도메인에 둔다**(재고부족은 notification 이 받지만 재입고는 구독 생명주기=notify+소진이 restock 관심사).
> catalog→(event)→restock→notification 으로 순환 없음.
>
> 추가·해제는 **멱등**이다 — 화면이 토글이라 사용자가 중복을 의도할 수 없고, 중복 요청은 더블클릭·재시도
> 같은 사고다. 거기서 409 를 주면 원인은 안 보이고 화면 상태만 어긋난다. DB 의
> `UNIQUE(member_id, product_id)` 가 최종 방어선. 조회 인덱스는 **따로 만들지 않는다** — 그 유니크
> 인덱스의 선두 컬럼이 `member_id` 라 "내 찜 목록" 쿼리가 그대로 탄다.
>
> 목록의 가격·재고·별점은 **찜한 시점이 아니라 지금 값**이다(주문 스냅샷과 정반대). 찜은 "나중에 살까"라
> 담아 두는 것이라 **값이 내렸는지·품절됐는지**를 봐야 쓸모가 있다. 삭제된 상품은 조회에서 빼되
> **지우지는 않는다** — readOnly 조회가 데이터를 고치면 "목록만 봤는데 뭔가 사라지는" 동작이 된다
> (장바구니는 지우지만 그건 결제 직전이라 정합성이 더 중요한 자리다).
>
> **관리자 회원 관리 (2026-07-28, B-11)**: 회원 목록·검색 + 상세(주문·반품·적립금·등급·이력). **조회 전용**
> (정지/역할변경 제외 — 쓰기라 파급이 다르다). ⚠ **크로스도메인을 facade 로 묶지 않았다** — 상세가 회원의
> 주문·적립금을 다 보여줘야 하는데, `OrderService` 가 이미 `member.Role` 을 참조하므로(order→member)
> member 가 order 를 부르면 **순환**이 된다. 그래서 **각 도메인이 자기 admin 조회를 소유**한다:
> member `/api/admin/members`(목록·기본상세), order `/api/admin/orders/by-member/{id}`(그 회원 주문·반품 —
> `status` 로 반품만 추림), point `/api/admin/points/{id}/account·history`. **상세 화면(프론트)이 셋을 조합**한다
> — 위시리스트에서 쓴 "클라이언트 조인"과 같은 수단이다. 탈퇴는 하드삭제(`MemberService.withdraw`)라 목록은
> **현존 회원만**; 탈퇴 회원의 과거 주문은 `order.buyer_nickname` 스냅샷으로 order 쪽에 남는다.
>
> **회원 정지·역할변경 (2026-07-28, B-11 후속, V30)**: 위 조회 전용의 **쓰기 후속**. `member.suspended`
> (boolean, `NUMBER(1)` — 상태가 이진이라 enum CHECK 트랩을 안 진다) + `changeRole`. **정지는 전면 차단**:
> 로그인·토큰갱신은 auth(`AuthService.login`/`refresh`)가, 주문은 order(`checkout` → `MemberService.isSuspended`
> 공개 API)가 막는다 — order→member 는 이미 있던 방향이라 순환 없음. ~~정지 시 refresh 토큰을 지워 기존 세션은
> access 만료(≤30분) 뒤 끊기고, 그 창의 주문은 checkout 가드가 닫는다.~~ → **그 30분 창은 2026-07-30 에 닫혔다**
> (E-2, 아래 「발급시각 컷오프」). **자기 계정은 조작 불가**(락아웃 방지,
> `CANNOT_MODIFY_SELF`). 조작 API 는 `/api/admin/members/{id}/suspend·unsuspend·role` — 조회와 같은 컨트롤러,
> `/api/admin/**` 보호. (조작 이력은 이제 감사 로그에도 남는다 — 아래 audit 도메인.)
>
> **최상위 관리자 SUPER_ADMIN (2026-07-28, V31)**: 위 정지/역할을 **계층화**했다(사용자 요청 — 처음엔 관리자끼리
> 서로 정지·강등이 가능해 위험). Role 에 SUPER_ADMIN 을 더하고 **엄격 분리**: 일반 ADMIN 은 USER 만 정지,
> **역할 변경과 관리자 정지는 SUPER_ADMIN 전용**, SUPER_ADMIN 계정은 아무도 못 건드린다(자기 포함). ⚠ 인가는
> **경로가 아니라 서비스 계층**에서 판단한다 — SUPER_ADMIN 은 `Role.authorities()` 로 `ROLE_ADMIN` 을 함께
> 받아 기존 `/api/admin/**`(hasRole('ADMIN'))를 그대로 통과하고, "관리자 조작은 SUPER만" 은
> `MemberAdminCommandService` 가 `actingRole`(JWT)로 가른다. SUPER 부여는 API로 불가(`CANNOT_GRANT_SUPER_ADMIN`),
> 오직 데이터로만. **V31**은 role 의 CHECK 를 시스템 이름이라 **동적으로 찾아 DROP**(`search_condition_vc` 조회)한 뒤
> named `ck_member_role`(USER/ADMIN/SUPER_ADMIN)로 재생성 — enum CHECK 트랩(orders.status 사고)의 정석 대응이다.
> ⚠ 특정 계정 승격(운영자ID)은 **신 jar 배포 후** 별도 UPDATE — 구 jar 는 SUPER_ADMIN 을 enum 으로 못 읽어
> 그 회원 로딩이 깨지므로 순서가 반대면 안 된다. 프론트의 `role==='ADMIN'` 비교는 `stores/auth` 의
> `isAdminRole`(SUPER 포함)로 모았다(흩어진 비교가 SUPER 를 관리 UI에서 배제하지 않도록).
>
> **관리자 감사 로그 (2026-07-28, V32)**: 위 조작들이 SLF4J 로그로만 흘러가 조회할 수 없던 걸,
> append-only 테이블(`admin_audit_log`)로 남기고 **SUPER_ADMIN 만 조회**한다. ⚠ **새 도메인 `domain/audit`**
> 를 따로 뒀다 — 감사는 회원만의 관심사가 아니라(주문·상품 조작도 앞으로 남길 수 있다) 특정 도메인에 종속시키면
> 안 된다. member 는 audit 을 **직접 부르지 않고** `AdminActionEvent` 를 발행하고(cross-domain 은 이벤트로만),
> audit 이 리스너(어댑터, 위임만)→CommandService(핸들러, 저장)의 3층으로 받는다. 기본 `@EventListener` 라
> **발행측 트랜잭션에 합류** — 감사 저장이 실패하면 조작도 롤백되고(감사 무결성), 권한 거부 등으로 조작이
> 롤백되면 감사도 안 남는다. 대상이 나중에 탈퇴·개명·강등돼도 이력이 깨지지 않게 그 시점의 actor_name·
> target_login 을 **스냅샷**으로 박는다(FK 없는 느슨한 UUID + 스냅샷 — restock·order 스냅샷과 같은 패턴).
> 조회 인가는 `/api/admin/audit/**` 를 `/api/admin/**`(ADMIN) **위에** `hasRole('SUPER_ADMIN')` 로 얹어
> 좁은 규칙이 먼저 매칭되게 한다. 조작당사자(ADMIN)가 자기 이력을 보는 구조를 막는다.
> ✅ **2026-07-30 (H-1)**: 이 도메인은 소스 10개에 **테스트가 0개**였다 — 있던 것은 "이벤트가 발행되는가"만
> 보는 단위 테스트라 **리스너 이후(저장·조회·권한)가 한 번도 실행된 적이 없었다.** 감사 로그는 쓰기만 하고
> 읽을 일이 없어 **고장 나도 아무도 모르는** 자리라 특히 위험했다. `AdminAuditIntegrationTest` 8건으로 고정
> (권한 401/403/**ADMIN 403**/200 · 행이 실제로 남는지 · 스냅샷이 탈퇴 후에도 읽히는지 · 필터·정렬 ·
> **롤백되면 감사도 사라지는지**를 REQUIRES_NEW 로 실측).
>
> **발급시각 컷오프 — access 토큰 즉시 무효화 (2026-07-30, E-2)**: 정지·강등이 **최대 30분간 효과가 없던**
> 구멍을 닫았다. 역할이 JWT 클레임에 박혀 있어 강등해도 옛 토큰의 `role` 은 ADMIN 이다(실측: `suspend` 는
> refresh 만 지웠고 `changeRole` 은 그것조차 안 했다). ⚠ **블랙리스트로는 못 막는다** — `TokenBlacklist` 는
> **jti 단위**라 여러 기기에 나가 있는 토큰들을 서버가 알지 못한다. 그래서 토큰을 모으는 대신 **시각 하나**를
> 남긴다: `TokenRevocationStore`(Redis `auth:revoked-before:<memberId>`)에 컷오프를 쓰고
> `JwtAuthenticationFilter` 가 `iat` 와 비교한다. TTL 은 **access 유효기간만큼만**(그 뒤엔 대상 토큰이 전부
> 자연 만료 — 블랙리스트가 "남은 만료시간만큼만" 올리는 것과 같은 판단). 경계는 **fail-closed**(`iat <= cutoff`):
> `iat` 가 초 정밀도라 `<` 로 비교하면 **강등과 같은 초에 발급된 토큰이 살아남는다.**
> 무효화 지점은 **다섯 곳** — 정지 · 역할변경 · 탈퇴(다른 기기 토큰) · 비밀번호 변경 · 비밀번호 재설정.
> 원래 백로그는 앞의 둘만 적었는데, `refreshTokenStore.delete` 옆에 *"다른 세션 무효화"* 주석이 달린 자리가
> **전부 같은 구멍**이었다(주석이 코드보다 앞서 있었다). ⚠ **역할변경은 refresh 를 지우지 않는다** — 재발급하면
> **새 역할**이 박히므로 그게 정상 경로다(정지는 반대로 재발급 자체를 막아야 한다). 프론트는 재발급 직후
> `/api/auth/me` 를 다시 읽어 `user.role` 을 맞춘다 — 안 하면 강등돼도 관리 메뉴가 계속 보인다.
>
> **회원 삭제 정리 + 관리자 강제 삭제 (2026-07-30, F-1·B-24, V35)**: 탈퇴가 회원 행만 지우고
> **다른 도메인 데이터를 전부 남기던** 것을 닫았다(실측: 고아 `point_account` 9행. 가장 뾰족한 것은
> 수령인·전화·주소가 든 `member_address` — 탈퇴는 "내 정보를 지워 달라"는 뜻인데 개인정보가 남았다).
> member 가 `MemberWithdrawnEvent` 를 발행하고 **각 도메인이 자기 것만 지운다**(남의 테이블을 직접
> 지우지 않는다 — 도메인 경계). 기본 `@EventListener` 라 **발행측 트랜잭션에 합류**해서, 정리가 실패하면
> 회원 삭제도 롤백된다("회원은 없는데 데이터는 남은" 상태를 만들지 않는다).
>
> | | 대상 |
> |---|---|
> | **지운다** | 배송지 · 찜 · 적립금(계정+이력) · 보유 쿠폰 · 알림 · **알림설정** · 재입고 구독 · **문의** |
> | **남긴다** | **주문**(매출 집계 근거) · **리뷰**(별점 집계 + 다른 고객의 판단 근거) · **공지**(관리자 콘텐츠) |
>
> 남길 수 있는 근거는 셋 다 **작성 시점 닉네임을 스냅샷**으로 들고 있어서다(`orders.buyer_nickname`·
> `review.author`·`notice.author`) — 회원이 없어도 표시가 깨지지 않는다. ⚠ 리뷰는 남기고 문의는 지우는
> 것은 **사용자 결정**이다(문의는 본인↔관리자 대화라 비밀글·개인정보가 들어갈 수 있다).
> ⚠ 문의는 **벌크 DELETE 가 아니라 건별 삭제**다 — 첨부 이미지 그룹 해제 경로를 건너뛰면 고아 이미지가 남는다.
>
> **B-24 관리자 강제 삭제**: `DELETE /api/admin/members/{id}` — **SUPER_ADMIN 전용**(되돌릴 수 없어
> 역할변경과 같은 급으로 봤다). 왜 필요했나: 탈퇴는 **본인 전용**이라 비밀번호를 모르는 계정은
> 정상 경로로 지울 방법이 아예 없었다(2026-07-30 잔재 점검에서 7/28 검증 계정을 발견했을 때 남은 선택이
> DB 직접 DELETE 뿐이었다). **본인 탈퇴와 같은 `purge` 경로를 공유**해 한쪽만 정리되는 어긋남을 구조로 막고,
> 감사 이력에 `MEMBER_DELETE` 로 남긴다(**V35** 가 CHECK 제약을 넓혔다 — V32 가 이름을 붙여 둬서 두 줄로 끝났다.
> V31 의 role 은 시스템 이름이라 동적 DROP 이 필요했던 것과 대비된다).
>
> ⚠ **빈 이름 충돌을 겪었다**: 6개 도메인에 리스너를 `MemberEventListener` 라는 **같은 클래스명**으로
> 만들자 스프링 기본 빈 이름이 겹쳐 컨텍스트가 아예 안 떴다(`ConflictingBeanDefinitionException`).
> → 도메인 접두사를 붙였다(`PointMemberWithdrawnListener` …). **패키지가 달라도 빈 이름은 단순명**이다.
>
> **배송지 주소록(2026-07-24, V18)**: 배송지는 `member.ship_*` 5컬럼 = **회원당 하나**였는데(V11),
> 별칭을 붙인 여러 주소(`member_address`)로 늘리고 그중 하나를 기본 배송지로 둔다.
> **기본 배송지는 회원당 최대 하나**이고 그 보장은 앱이 아니라 **DB**가 한다 —
> Oracle에 부분 유니크 인덱스가 없어 `CASE WHEN is_default=1 THEN member_id END`의
> **함수 기반 유니크 인덱스**로 같은 효과를 낸다(주문번호 유니크 V15와 같은 "최종 방어선" 성격).
> 그래서 서비스는 "옛 기본 해제 → **flush** → 새 기본 지정" 순서를 지켜야 한다. 순서가 뒤집히면 ORA-00001이다.
>
> `member_address.member_id`에는 **진짜 FK를 건다**(`ON DELETE CASCADE`). `member_coupon.member_id`가
> "FK 아님(느슨한 참조)"인 것과 다른데, 그건 **도메인 간** 참조라 경계 때문에 느슨하게 뒀고
> 이건 **member 도메인 안**이라 MSA로 쪼개도 member와 함께 움직이기 때문이다.
> CASCADE가 없으면 주소가 있는 회원은 **탈퇴 자체가 FK 위반으로 실패**한다.
>
> ⚠ **첫 번째 "순수 추가가 아닌" 마이그레이션**이라 expand/contract로 나눴다 —
> **V18은 추가만**(테이블 신설 + 값 복사), `member.ship_*` **DROP은 V20**(신 코드 배포 뒤 — V19는 위시리스트가 가져갔다).
> **둘 다 2026-07-24에 끝났다** — V18 배포·검증 후 같은 날 V20으로 컬럼을 지웠다.
> **V20은 배포가 필요 없었다**(코드 변경 0, 마이그레이션은 통합 테스트가 espdb에 적용).
> 엔티티 매핑을 V18에서 미리 걷어낸 덕에 생긴 부수효과다.
> `ddl-auto=validate`라 한 번에 DROP하면 통합 테스트가 Flyway를 공유 espdb에 적용하는 순간
> **운영 구 jar가 재기동 불가**가 된다(V6 닉네임 UNIQUE 사고의 강화판 — 그건 신규 가입만 막았지만
> 컬럼 DROP은 기존 조회를 통째로 깬다). 그 사이 이중 진실이 되지 않게 **Member 엔티티에서 매핑을 걷어냈다**
> — 규율이 아니라 구조로 막는다(validate는 매핑 안 된 여분 컬럼을 문제 삼지 않는다).
> `MemberResponse.ship*`는 **응답 계약을 유지**한 채 출처만 주소록의 기본 항목으로 바꿨다.
>
> ⚠ `orders.ship_*`는 무관하다 — 그건 주문 시점 **스냅샷**이고, 주소록을 고쳐도 과거 주문은 그대로다.
>
> **주문 상태**: `ORDERED → PAID → SHIPPED → DELIVERED` (+CANCELLED, ORDERED·PAID만). 취소 시 재고 복원.
> 2026-07-16에 SHIPPED까지 만들고, **2026-07-23에 배송 추적(V13)** 을 붙이며 DELIVERED를 추가했다.
> 네 시점이 모두 DB에 기록된다(`created_at`·`paid_at`·`shipped_at`·`delivered_at` + `cancelled_at`) —
> 주문 상세의 진행 스텝은 이 실제 기록을 그린다(화면에서 지어낸 값이 아니다).
>
> **배송 추적(2026-07-23, V13)**: 발송 처리에 **운송장(택배사·송장번호)이 필수**다. 운송장 없이 발송하면
> 나중에 채워 넣을 경로가 없어 그 주문은 영영 추적 불가가 되므로, 발송 전이와 운송장 등록을 한 트랜잭션으로 묶었다.
> 택배사는 `DeliveryCarrier` enum이고 **조회 URL은 설정**(`glassvue.delivery`)이다 — 서버가 완성한
> `trackingUrl`을 응답에 실어 화면이 택배사별 URL 형식을 알지 않게 한다.
>
> ⚠ **기본값은 앱 안의 예시 페이지(`/mock-tracking`)이고, 실제 택배사 사이트를 가리키지 않는다.**
> 처음엔 enum에 실제 조회 URL을 박았는데 두 가지가 잘못이었다 — ①주문이 전부 가짜라 송장번호도
> 가짜여서 실제 택배사로 보내봐야 "조회 결과 없음"만 나오고 ②통제할 수 없는 외부 의존이 생긴다
> (택배사가 URL을 바꾸면 링크가 조용히 깨지는데 깨진 걸 알 방법이 없다). §1 "미리 만들지 않는다"에도 어긋난다.
> 실제 배송이 필요해지면 `tracking-url` 아래에 택배사별 실제 URL을 넣기만 하면 된다 — **코드 변경 없이**
> (PG를 seam으로 남겨둔 것과 같은 방식). 빈 문자열로 두면 링크 자체를 만들지 않는다.
>
> `orders.ship_carrier`에는 **일부러 CHECK를 걸지 않았다**(택배사는 늘어날
> 값이라 CHECK를 걸면 추가할 때마다 제약 교체 마이그레이션이 필요하다 — 검증은 enum이 한다).
> 반대로 `orders.status`는 상태 전이 규칙이라 DB CHECK를 유지한다(V13에서 `ck_orders_status`로 이름을 붙였다).
> 배송완료 전이는 지금 **관리자 수동**이다 — 택배사 웹훅 연동은 이후 단계(PG와 같은 자리).
> **결제(PAID) 전이는 지금 플레이스홀더** — `POST /orders/{id}/pay`가 상태만 바꾼다(실제 돈 안 움직임). **실제 PG 연동은 MSA 단계로 보류(2026-07-16 결정)**.
> PG 붙일 때: ①`OrderService.pay()`(seam)는 그대로, 그 앞단을 "PG 서버 금액검증 후 pay() 호출"로 교체 ②**`payment` 도메인/테이블 신설**(provider·거래ID·금액·영수증, 주문과 별도 테이블) ③webhook(멱등) ④환불 연동(취소 시 PG 환불 API). **PG사 미정 상태로 payment 도메인을 선설계하지 않는다**(§1 "미리 만들지 않는다").
>
> **관리자 홈 대시보드 (2026-08-03, B-16, 마이그레이션 없음)**: 7/28~7/31 에 관리 화면을 계속 쌓았는데
> (회원 관리·감사 이력·쿠폰 지정·강제 삭제) **`/admin` 경로 자체가 없어** 관리자는 「관리 ▾」 드롭다운을
> 기억해야 일을 시작할 수 있었다. 주소창에 `/admin` 을 쳐도 404 규칙에 걸려 **`/products` 로 튕겼다**
> (그 규칙은 관리 화면 존재를 숨기려고 일부러 만든 것이라, 정상 경로가 생기기 전까지는 옳게 동작한 셈이다).
>
> ⚠ **매출을 다시 그리지 않는다.** `/admin/stats`(C-11, 2026-07-24)가 이미 요약·30일 추이·상품별 TOP 을
> 그린다. 대시보드는 **오늘·이번 달 한 줄 + 링크**만 두고 나머지는 그 화면으로 보낸다 — 같은 숫자를 두
> 화면이 그리면 어느 쪽이 최신인지 헷갈리고 고칠 자리도 둘이 된다. 그래서 이 기능의 실제 값은
> **"지금 내가 처리해야 할 게 뭔가"** 하나다(발송 대기·반품 요청·재고 부족).
> ⚠ `ORDERED`(결제 대기)는 **고객이 할 일**이라 처리 대기에 넣지 않았다 — 관리자가 아무것도 할 수 없는
> 줄이 섞이면 숫자가 매일 의미 없이 커진다.
>
> **새 API 는 하나** — `GET /api/admin/products/low-stock`(catalog). 주문 건수·매출은 기존
> `/api/admin/orders/counts`·`/api/admin/stats/sales` 를 그대로 쓴다. ⚠ 백로그는 *"새 API 없이 모아
> 보여주는 화면 하나"* 라고 적었지만 **재고 부족만은 조회 경로가 없었다** — `catalog.low-stock-threshold`
> 는 `StockRunningLowEvent` **발행용 임계값**일 뿐 그 상태를 물어볼 엔드포인트가 아니다.
>
> 이 API 의 판단 셋: ①**기준값을 응답에 실어 준다**(`threshold`) — 화면이 *"5개 이하"* 를 스스로 적으면
> 설정을 바꾼 순간 문구가 거짓말이 된다(혜택 문구를 서버가 줄 때만 노출하기로 한 G-2 와 같은 자리).
> 그리고 그 값은 **재고 부족 알림과 같은 것**이라 알림과 대시보드가 갈리지 않는다.
> ②**`HIDDEN` 상품 제외 · `SOLD_OUT` 포함** — 숨긴 상품은 팔지 않으니 채울 이유가 없지만, 품절 표시는
> 관리자가 손으로 붙이는 것일 뿐 재입고가 필요한 건 그대로다. ③**`count`(전체)와 `items`(상위 8줄)를
> 나눈다** — `items.size()` 를 숫자로 쓰면 "10건 넘는데 10" 이 된다. 목록의 줄마다 `productId` 를 실어
> **고치러 갈 길**을 준다(숫자만 주면 결국 상품을 다시 찾아야 한다).
>
> ⚠ **`count` 와 `items` 는 쿼리가 둘이라 조건이 갈릴 수 있다.** 변형 주입으로 한쪽에서만 `HIDDEN`
> 조건을 빼 봤더니 **서로 다른 테스트가 하나씩 잡았다**(count 쪽 2건 · items 쪽 1건) — 둘을 따로 검증한
> 것이 값을 한 자리다. 조건을 고칠 일이 생기면 **반드시 양쪽을 함께** 고친다.
>
> 곁가지로 관리자 주문 목록에 **`?status=` 진입점**을 열었다(대시보드 카드가 할 일을 집어서 보낸다).
> ⚠ 모르는 값은 통과시키지 않는다 — 서버가 400 을 내면 화면에는 그게 **"주문이 없다"로 보인다.**
>
> **가입 약관 동의 (2026-08-03, B-21, V37)**: 개인정보(이메일·닉네임)를 수집·저장하면서
> **동의 절차가 아예 없었다** — `SignupRequest` 4필드, 가입 화면 체크박스 0개(실측).
>
> ⚠ **이 마이그레이션의 핵심은 백필을 안 한 것이다.** `member.terms_agreed_at`·`marketing_agreed_at`
> 둘 다 **nullable + 백필 없음**. V34(`email_verified`)는 `DEFAULT 0` 백필이 **사실과 맞았지만**
> ("지금까지 아무도 인증한 적이 없다"), 여기서 `sysdate` 를 넣으면 **동의한 적 없는 사람에게 동의
> 시각이 생긴다.** 하필 "동의를 받았다"는 **근거**로 쓸 값이라 **거짓이 가장 비싼 자리**다.
> → `NULL` = 「동의 기록 없음」. 그래서 **소급 적용도 없다** — 기존 회원은 재동의 없이 계속 로그인된다
> (E-3 비밀번호 정책과 같은 판단). ⚠ 이걸 **테스트로 고정**해 뒀다: 안 그러면 나중에 *"동의 안 한 회원
> 로그인 차단"* 을 무심코 넣어 **기존 회원 전부를 잠근다.**
>
> ⚠ **약관과 개인정보 처리방침은 한 컬럼**이다. 둘 다 필수라 항상 같이 참/거짓이고, 나누면
> *"약관만 동의한 회원"* 이라는 **일어날 수 없는 상태**가 표현 가능해진다. 버전 관리가 필요해지면
> 그때 `member_agreement` 테이블로 간다(BACKLOG B-21).
>
> ⚠ **마케팅 동의를 `NotificationType` 에 넣지 않았다.** 설정 화면이 `values()` 를 통째로 그리므로
> 값을 더하면 **토글이 저절로 생기는데 보내는 코드가 없다**(no-op 배선). 반면 컬럼은 **근거**라
> 성격이 다르다 — **동의는 소급해서 받을 수 없어서** 채널이 없어도 먼저 받아 둔다.
> (§1 "미리 만들지 않는다"의 경계 사례 — *배선*은 미리 만들지 않지만 *근거*는 그때 못 만든다.)
>
> 검증은 DTO(`@AssertTrue`)가 아니라 **서비스 + 전용 `ErrorCode`(`AUTH-400T`)** 다: 동의 누락은
> 형식 오류가 아니라 **정책**이고(E-3 을 서비스에 둔 것과 같은 판단), 프론트가 **코드로 어느 체크박스를
> 붉힐지** 골라야 한다. `null` 과 `false` 를 함께 막는다(필드를 안 보내는 것도 "동의 안 함").
> **동의 시각은 서버가 찍는다** — 요청에 시각을 받지 않는다(근거가 조작 가능해진다).
>
> 관리자 회원 상세(`AdminMemberResponse`)에 동의 시각을 실었다 — **조회할 수 없는 동의 기록은 근거
> 구실을 못 한다.** ⚠ 화면은 `null` 을 「미동의」가 아니라 **「기록 없음」** 으로 읽어야 한다.
>
> **마케팅 알림 발송 (2026-08-03, B-21 후속, 마이그레이션 없음)**: 위에서 받은 동의가 **실제로 쓰이는
> 자리**를 만들었다(사용자 지적에서 나옴). 지금까지 알림은 전부 이벤트에서 자동 생성됐는데
> (주문·재고·재입고·문의) 이게 **사람이 알림을 써서 보내는 첫 사례**다 — 그래서 이벤트 3층을 쓰지
> 않는다(반응할 도메인 사건이 없고 관리자 요청이 시작점이다).
>
> ⚠ **이 기능의 설계 전부는 「동의와 수신 거부를 합칠 것인가」였다.** 합치지 **않았다**:
>
> | 값 | 뜻 | 성질 | 저장소 |
> |---|---|---|---|
> | `member.marketing_agreed_at` | **동의했나** | 일어난 사건, 안 변함 (근거) | member 컬럼 |
> | `NotificationType.MARKETING` 토글 | **지금 받고 싶나** | 자유롭게 바뀜 (선호) | `member_notification_pref` |
>
> 합쳤다면 **토글을 끌 때 동의 기록이 지워져** *"이 사람이 언제 동의했었나"* 에 영영 답할 수 없다.
> 백필을 안 한 이유(동의 기록에 거짓을 쓰지 않는다)와 **같은 원칙의 다른 얼굴**이다 — 동의 이력은
> 지우는 게 아니라 쌓는 쪽이다. ⚠ G-2 의 *"설정과 데이터가 갈린다"* 문제와 **다르다**: 저건 **같은
> 것**을 두 군데가 말해서 생긴 문제였고, 여기는 **다른 둘**이라 나눠 두는 게 맞다.
>
> **판단 주체도 둘로 갈린다** — 「동의했나」는 member 가 목록(`marketingAgreedIds()`)으로 답하고,
> 「지금 받고 싶나」는 `NotificationCommandService.create` 가 **이미 하던 일**이다(설정이 꺼져 있으면
> 만들지 않는다). 도메인 경계는 `StockAlertHandler` → `MemberService.adminIds()` 선례를 그대로 따랐다.
>
> `create()` 를 `void` → `boolean` 으로 바꿨다 — **대상 수를 그대로 "보냈다"고 세면 수신 거부자까지
> 발송으로 집계**된다. 응답은 `agreed`·`sent`·`optedOut` 을 **나눠서** 준다(합치면 *"동의자가 적어서"* 와
> *"다들 꺼서"* 를 구분할 수 없는데 대응이 완전히 다르다).
>
> ⚠ **감사 로그는 남기지 않는다** — `admin_audit_log` 는 `target_id`/`target_login` 이 **NOT NULL** 인
> 「관리자가 회원 *한 명*에게 무엇을 했나」 모델이라 N명 브로드캐스트와 안 맞는다. 억지로 넣으면
> `target` 에 더미를 채워야 하고 그건 **감사 기록에 거짓을 쓰는 것**이다. 발송 사실은 `notification`
> 행 N개로 이미 남는다 → `AuditAction` 확장과 CHECK 제약 마이그레이션이 통째로 불필요해졌다.

---

## 6. 미래 인프라 로드맵

> **§6.0**은 단계 무관하게 *지금/곧* 도입 후보다. 그 아래(관측·MSA 인프라)는 **MSA/이커머스 단계** 계획으로, 모노레포 연습 단계에선 설치하지 않는다.
> (현재 인프라: Nginx · Oracle 19c · Redis 7.4.7 — systemd 서비스)

### 0. 조기 도입 후보 (단계 무관 — 지금/곧)

MSA를 기다릴 필요 없는, 개발 편의·보안·에러 가시성 개선. 현재 단일 VM·systemd에서 바로 적용 가능.

| 기술 | 판단 | 시점 | 메모 |
|---|---|---|---|
| **P6SPY** | ✅ **완료** (2026-07-16) | dev 프로파일 한정 | JDBC 가로채 실제 SQL + 바인딩값 + 실행시간 로깅. QueryDSL 동적쿼리·N+1 육안 확인용. **운영 프로파일 제외**(오버헤드·민감정보 로깅). *구현 메모*: `p6spy-spring-boot-starter`(gavlyukovskiy)는 Boot 4.1 자동설정 호환이 불확실해 채택하지 않고, **plain `p6spy` + dev 프로파일에서 datasource URL만 `jdbc:p6spy:…`로 재작성**(자동설정 무의존)했다. 운영은 순수 oracle URL이라 p6spy 코드 경로를 안 탐. 포맷/카테고리는 `spy.properties`(SLF4J·한 줄·결과셋 제외) |
| **HTTPS** | ✅ **완료** (2026-07-16) | — | JWT를 평문으로 흘리지 않기. **nginx에서 TLS 종단(:443), 백엔드는 내부 HTTP(:8080) 유지**. 내부 VM이라 **self-signed**(SAN에 IP 포함 — 브라우저는 CN 무시·SAN만 검증) 10년. **SAN: `IP:192.168.50.14, IP:127.0.0.1, DNS:localhost, DNS:glassvue.local`**(2026-07-22 재발급 — 서버 IP가 `.36`→`.14`로 바뀌어 SAN이 어긋났다). ⚠ **2026-07-30: 그 뒤에 적어 둔 *"IP를 정적으로 고정했으므로 재발 없음"* 은 틀렸다** — 어댑터가 NAT 대역으로 바뀌어 IP가 `10.0.2.15`가 됐다(접속은 호스트에서 포트포워딩). SAN 의 `IP:192.168.50.14` 는 이제 죽은 값이고, **살아 있는 것은 `IP:127.0.0.1`·`DNS:localhost`** 다 — 포워딩으로 `https://localhost/` 로 들어가면 SAN 에 걸려 경고가 없다. 새 IP 로 직접 접속할 일이 생기면 재발급이 필요하다(그때는 SAN 을 IP 대신 DNS 로 잡는 쪽이 낫다). 원본 `/home/ecstel/nginx-tls/2026-07-22/`, 이전 인증서는 `/etc/nginx/ssl/*.bak`으로 백업. **80→443 301 리다이렉트**, 방화벽 443 개방. 프론트는 `/api` 상대경로 + JWT를 localStorage에 둬서 코드·재배포 불필요(secure 쿠키 이슈 없음). 인증서 원본 `/home/ecstel/nginx-tls/`, 배포본 `/etc/nginx/ssl/`. 공인 도메인 생기면 Let's Encrypt로 교체 |
| **인앱 알림 SSE** (nginx) | ✅ **완료** (2026-07-24) | — | 인앱 알림 실시간 푸시가 SSE라, nginx가 그 경로를 **버퍼링하면 이벤트가 뭉쳐 즉시 안 나간다**(토스트가 안 뜬다). `/etc/nginx/conf.d/glassvue.conf` 에 `location = /api/notifications/stream` 을 `location /api/` **와 별도**로 두고 `proxy_buffering off`·`gzip off`·`chunked_transfer_encoding on`·`proxy_read_timeout 3600s`. exact(`=`) 매치라 프리픽스 `/api/` 보다 우선(파일 순서 무관). 안 해도 알림은 DB에 남아 **벨/뱃지는 재조회로 보인다** — 즉시성(토스트)만 잃는 우아한 열화. 서버측은 `SseEmitter`(회원별 레지스트리) + `@Scheduled(15s)` 하트비트로 유휴 연결 유지. ⚠ 인증: 브라우저 기본 `EventSource`가 Authorization 헤더를 못 실어 **프론트가 fetch 스트림 + Bearer** 로 연결(토큰 URL 노출 회피). 상세 `handoffs/2026-07-24-handoff.md` §11 |
| **비밀번호 재설정** (B-10) | ✅ **완료** (2026-07-28) | — | "비밀번호 잊음" 흐름. 아이디로 **단발성 토큰** 발급(`PasswordResetTokenStore`, Redis `auth:reset:<token>`=memberId, 30분 TTL, `getAndDelete`로 재사용·경합 차단) → 새 비번. `RefreshTokenStore`와 반대로 **토큰을 키**로 둬(회원당 1개가 아니라 토큰당 1개) 재발급이 이전 링크를 조용히 무효화하지 않고 O(1) 소비. 변경 시 refresh 폐기(다른 세션 무효화, `changePassword`와 동일). **열거 방지**: 없는 아이디도 200. ✅ **2026-07-29 완성** — 보낼 주소(B-13)와 발송 채널이 같은 날 붙었다. 채널은 **로컬 메일 캐처**(Mailpit `127.0.0.1:1025`)라 밖으로 나가지 않고, 코드는 `global/mail/Mailer` 한 곳. ⚠ 운영 차단은 `application.yml` 에 **`spring.mail` 키를 두지 않는 것**으로 보장한다 — *"빈 기본값이면 꺼진다"* 는 **틀렸다**(프로퍼티가 존재하면 자동설정이 빈을 만들고 localhost 로 폴백한다 — 통합 테스트가 실제로 메일을 보내 드러났다). `MailerAutoConfigOffTest` 가 고정. ✅ **소유 인증도 같은 날 붙었다**(B-14, V34) — 6자리 인증번호를 메일로 보내 확인하고 `member.email_verified` 에 남긴다. ⚠ 주소를 바꾸면 인증이 자동으로 풀린다(인증은 주소에 대한 것). ⚠ 미인증 주소로도 재설정 링크는 보낸다(사용자 결정 — 안 보내면 오타 낸 사람이 계정을 못 찾는다). 링크는 dev 에서 응답에도 실어(`auth.password-reset.expose-token`, base=false·`application-dev.yml`=true) 화면에서 확인한다. 운영은 기본 프로파일로 떠(systemd `ExecStart`에 `--spring.profiles.active` 없음) 절대 노출 안 됨. 마이그레이션 없음(Redis). 상세 `handoffs/2026-07-28-handoff.md` |
| **아이디 찾기** (G-1) | ✅ **완료** (2026-07-31) | — | 재설정은 **아이디를 알아야** 시작되므로, 아이디를 잊으면 들어올 방법이 아예 없었다. `POST /api/auth/find-id`(이메일) → **아이디를 메일로**. 재설정의 규칙을 그대로 물려받는다 — 없는 주소도 **200**, 값은 화면이 아니라 메일로, 카운트는 **조회 전**. ⚠ **가드는 공유하지 않는다**: 저쪽 키는 아이디, 이쪽은 이메일이라 합칠 수 없고, 합치면 *아이디 찾기 3회에 재설정 예산이 소진*된다 — 공유하는 것은 `AttemptCounter` 구현뿐이고 정책(주소 3 / IP 10 · 10분, `AUTH-429F`)은 각자 갖는다. ⚠ **dev 노출 갈래를 만들지 않았다** — 재설정의 `expose-token` 은 **링크**지만 아이디는 **신원**이라 성질이 다르다(dev 확인은 Mailpit). ⚠ 조회 전 `Member.normalizeEmail` 필수 — 저장은 소문자라 정규화를 빠뜨리면 대문자로 가입한 사람이 자기 아이디를 영영 못 찾는다. 정지 계정도 알려준다(아이디는 상태와 무관한 식별자). 마이그레이션 없음. 상세 `handoffs/2026-07-31-handoff.md` §3 |
| **Sentry** (에러추적) | ⏸ **보류 → 관측/MSA 단계** (2026-07-16 재판단) | 관측 스택과 함께 | 예외/에러 그루핑·릴리스 추적, **프론트(Vue)+백(Spring) 동시 커버**. *재판단 이유*: 의미가 있으려면 이벤트를 받을 곳(DSN→sentry.io SaaS 또는 자체호스트)이 필요한데, 자체호스트는 Docker+무거운 스택(지금 로드맵상 보류)이고 SaaS는 외부 계정·전송 필요. **모노레포 연습 단계엔 오버엔지니어링** — 관측 스택(Alloy/Loki/Prometheus/Grafana) 도입 시점에 함께 넣는다. 메트릭·로그·트레이스와 별개의 "에러 전용" 도구 |
| **Spring Batch** | ⏸ 보류 | 트리거 충족 시 | 스케줄러는 이미 사용 중(`@Scheduled` 조회수 플러시 = 미니 배치). Batch는 **청크·재시작·대량 row** 조건에서만 값을 함. 후보 작업: ①일 매출 집계 ②미결제 주문 자동취소(재고복원) ③쿠폰 만료. 그 전엔 오버킬. 다중 인스턴스 스케줄 중복 시 Quartz 클러스터 |

### 관측 (Observability)

```
Spring Boot ──(로그)──┐
 (Actuator /prometheus)│
                       ▼
                  Grafana Alloy ──(로그)──▶ Loki ──────┐
                       └────────(메트릭)──▶ Prometheus ─┤
                                                         ▼
                                                      Grafana (대시보드)
```

- **수집기는 Alloy** — Promtail은 deprecated(EOL 수순), 그 전신 Grafana Agent도 EOL. Alloy(OTel Collector 기반)가 로그·메트릭·트레이스를 단일 에이전트로 처리한다. **Promtail 신규 도입 금지.**
- 로그: 앱 로그 → Alloy tail → **Loki**
- 메트릭: Spring에 `micrometer-registry-prometheus` 추가 → `/actuator/prometheus` → **Prometheus**
- 시각화: **Grafana** (Loki + Prometheus 데이터소스)
- **트레이스/APM**: OTel + **Grafana Tempo**(Alloy 스택에 자연 결합 — 메트릭·로그·트레이스를 한 Grafana에)로 분산추적. 대안 **Pinpoint**(Naver OSS, 바이트코드 에이전트·서버맵 — HBase 필요로 운영 무거움, 한국 생태계 학습용). **Datadog·Jennifer는 상용/유료 → 연습 단계 제외**(업계 인지만). 에러 그루핑은 별개로 **Sentry**(§6.0)가 담당.

#### LGTM 스택과의 관계 (2026-07-29 정리)

**"LGTM 도입"은 새 결정이 아니라 위 스택의 다른 이름이다.** LGTM = **L**oki(로그)·**G**rafana(시각화)·
**T**empo(트레이스)·**M**imir(메트릭). 위 그림과 대조하면 L·G·T 는 **이미 그대로 계획돼 있고**, 다른 건 M 하나다.

| LGTM | 우리 계획 | 판단 |
|---|---|---|
| **L**oki | Loki | 동일 |
| **G**rafana | Grafana | 동일 |
| **T**empo | Tempo (트레이스) | 동일 |
| **M**imir | **Prometheus** | ⚠ **Prometheus 유지.** Mimir 는 Prometheus 호환 **장기보존·수평확장 백엔드**다 — 여러 Prometheus 를 모아 클러스터로 굴릴 때 값을 한다. **단일 VM·단일 앱엔 운영 비용만 는다.** 보존기간이 모자라거나 인스턴스가 여러 대가 될 때 그때 얹는다(Prometheus → Mimir 는 remote_write 로 갈아끼우는 경로라 나중이 비싸지 않다) |

→ **결론: LGTM 을 "도입"하는 게 아니라, 이미 계획된 Alloy+Loki+Prometheus+Grafana 를 실제로 깔고
그 위에 Tempo 를 얹으면 그게 LGTM 이다.** 도입 시점도 그대로(아래 「도입 조건」).

#### 상용 APM (MaxGauge · Dynatrace · Jennifer) — ⏸ **보류(학습 트랙 후보)**, 제외 아님

> ⚠ **판단을 한 번 바꿨다 (2026-07-29, 사용자 전제 확인).** 처음엔 "유료라서 ❌ 제외"로 적었는데,
> **이 프로젝트는 학습용이고 운영으로 쓰지 않으므로 비용은 제약이 아니다**(사용자). 그래서 **비용 사유를
> 걷어내고** 남는 게이트만 다시 적는다. 버린 근거를 지우지 않고 남기는 이유는, 안 그러면 나중에
> "왜 유료를 뺐지?"로 되돌아가기 때문이다(§4-2).

**비용을 빼도 게이트가 셋 남는다. 그리고 그중 첫째가 진짜 이유다.**

1. **볼 게 없다(가장 큰 이유).** APM 의 값은 **트래픽·서비스 수**에서 나온다. 지금은 단일 VM·단일
   모놀리스·주문 수십 건이라, 무엇을 깔아도 **화면이 텅 빈 채 예쁘기만 하다.** Sentry 를 보류한 것과 같은 판단.
   → **부하를 만들 수단(k6, BACKLOG D)이 먼저다.** 이건 돈으로 못 사는 선행 조건이다.
2. **평가판은 기간이 있다.** 대부분 15~30일이라, **켜 두면 그냥 만료된다.** 볼 게 준비된 뒤 **짧게 태워야**
   값이 나온다 — 즉 순서가 뒤바뀌면 기회를 한 번 버리는 셈.
3. **구매 가능성은 도구마다 다르다.** ⚠ 비용을 감수하더라도 **개인이 살 수 있는 제품이 아닐 수 있다** —
   국산 상용(MaxGauge·Jennifer)은 대체로 **기업 영업 계약** 기반이라 개인 구매 창구 자체가 없는 편이다.
   실제 도입 전에 **평가판 제공 여부·형태를 먼저 확인**할 것(아래 표).

| 도구 | 성격 | 학습 가치 | 도입 시 확인할 것 | 그 전까지의 대체 |
|---|---|---|---|---|
| **MaxGauge** (엑셈) | **DB(Oracle) 성능 모니터링** — 세션·SQL 단위 실시간 추적 | ⚠ 나머지 둘과 **결이 다르다**(앱 APM 이 아니라 **DB 전문**). 국내 금융·SI 에서 Oracle 튜닝 도구로 자주 언급됨 | 개인 평가판 창구가 있는지(영업 문의형일 가능성). 에이전트 + 리포지토리 DB 가 별도로 필요해 **설치가 무겁다** | ①**P6SPY**(완료, dev SQL·바인딩·실행시간) ②`v$` 뷰 직접 조회 ③**Statspack**(무료)<br>⚠ **AWR/ASH 는 무료가 아니다** — Diagnostic Pack(유료 옵션). 습관적으로 쓰기 전에 라이선스 확인 |
| **Dynatrace** | 상용 APM (OneAgent 자동계측·AI 근본원인 분석) | 자동계측·서비스맵을 **직접 보는 경험**. 셋 중 **개인이 평가판을 열기 가장 쉬운 쪽** | ⚠ **SaaS 형태면 텔레메트리가 외부로 나간다.** 우리 원칙은 "외부 전송 없이 로컬에서" 였으므로, **켜기 전에 무엇이 나가는지 확인**하고 켤 것(운영 데이터가 아니라 검증 데이터만 흐르는 상태에서) | **OTel + Tempo**(계획됨). 자동계측은 **OTel Java Agent** 로 상당부분 대체된다 |
| **Jennifer** (제니퍼소프트) | 상용 APM (국산, 실시간 X-View) | 국내 채용 공고에 자주 등장 — **이름과 화면을 아는 것**의 값 | 위 MaxGauge 와 같음(영업 계약형일 가능성) | 동일 |

→ **결론: "안 쓴다"가 아니라 "순서가 있다".** 부하를 만들 수 있게 된 뒤(k6) OSS 로 먼저 원리를 익히고
(OTel+Tempo / Pinpoint), 그 위에서 **상용 평가판을 짧게 태워 비교**하면 *"상용이 무엇을 더 해 주는가"* 가
비로소 보인다. 반대로 지금 깔면 **비교 대상이 없어 "좋아 보인다"밖에 못 얻는다.**

#### 학습 트랙 — 운영 필요와 무관하게 "배우려고" 넣는 경우 (2026-07-29 신설)

> **왜 이 절이 생겼나**: 이 문서의 판단은 그동안 **"운영에 필요한가"** 한 축으로만 적혀 있었다.
> 그런데 이 프로젝트는 **학습이 목적이고 운영하지 않는다**(사용자). 그래서 §1 "미리 만들지 않는다"를
> 곧이곧대로 적용하면 **배우려고 넣는 것까지 전부 막힌다** — 실제로 C-8(상품 옵션)은 그 예외를
> 의식적으로 선택한 사례다. 축을 둘로 나눠 둔다.

**두 트랙의 판정 기준이 다르다.**

| | 운영 트랙 | 학습 트랙 |
|---|---|---|
| 질문 | "지금 이게 없어서 아픈가?" | "이걸 넣으면 **무엇을 겪게 되는가?**" |
| 통과 조건 | 실제 문제·요구가 있을 것 | **겪을 거리가 실재할 것**(볼 게 있어야 한다) |
| 실패 모드 | 오버엔지니어링 | **no-op 설치** — 깔았는데 화면이 비어 아무것도 안 배움 |

**학습 트랙 3원칙**

1. **운영 VM 을 실험대로 쓰지 않는다.** 지금 `192.168.50.14` 한 대에 Oracle·Redis·nginx·백엔드가
   전부 systemd 로 떠 있다. 학습용 설치는 **별도 VM 또는 별도 포트/프로파일**로 격리한다
   (검증 계정 `esptest`·Redis 별도 db 를 쓰는 것과 같은 이유).
2. **볼 게 먼저, 도구는 나중.** 관측·APM 계열은 **부하 생성 수단(k6)** 이 선행 조건이다.
   순서를 지키면 도구가 *답*을 보여주고, 어기면 도구가 *빈 화면*을 보여준다.
3. **"무엇을 배웠나"를 남긴다.** 학습 트랙으로 깐 것은 핸드오프에 **관찰한 것**(숫자·화면·놀란 점)을
   적는다. 안 그러면 "깔았다"만 남고 지식은 안 남는다.

**권장 순서** — 뒤엣것부터 하면 앞의 것이 왜 부족했는지를 영영 모른다.

```
① 부하 생성 (k6)  ─ 볼 게 생긴다. 선착순 발급 기능이 선행(BACKLOG D)
        ↓
② OSS 관측 (Alloy+Loki+Prometheus+Grafana → +Tempo)  ─ = LGTM. 원리를 여기서 익힌다
        ↓
③ 상용 평가판 (Dynatrace 등)  ─ ②와 비교해야 "무엇을 더 해 주는가"가 보인다. 기간 있으니 짧고 굵게
        ↓
④ Docker → (원하면) k3s/kind  ─ 이미지가 있어야 올릴 게 있다
        ↓
⑤ RabbitMQ → (replay 요구가 생기면) Kafka  ─ 둘의 차이를 몸으로 안다
```

⚠ **MQTT 는 이 순서에 없다.** 다른 것들은 "지금은 실효가 없다"지만, MQTT 는 **대응하는 문제 자체가
이 도메인에 없다**(아래 재검토 표). 배우고 싶다면 이커머스가 아니라 **디바이스가 있는 별도 토이 프로젝트**가 맞다.

#### ✅ 2026-07-31 — JaCoCo 도입 (H-5): 학습 트랙 통과 조건을 **처음으로 실제 충족한** 도구

관측·APM 계열이 전부 *"볼 게 없다"* 로 보류된 것과 **정반대 사례**라 여기 남긴다. 테스트가 **469개**
있으니 계측할 대상이 실재했고, 깔자마자 **몰랐던 것이 나왔다**(§원칙 3 "무엇을 배웠나"):

- 전체 **명령어 87% · 분기 72%**(라인 87%). "테스트가 469개" 라는 감각보다 **분기 쪽이 훨씬 얇았다.**
- **`OrderNotificationHandler` 4% · `StockAlertHandler` 4% · 분기 0%** — 주문·재고 알림의 **진짜 주체가
  한 번도 실행된 적이 없다.** 원인은 구조적이다: `@Async` + `AFTER_COMMIT` 리스너는 `@Transactional`
  테스트에서 커밋이 없어 **뜨지 않는다**(B-15 에서 핸들러를 직접 부른 이유가 이것). 리스너는 100% 인데
  핸들러가 4% 인 게 그 증거다 → **H-6 으로 같은 날 메웠다**(핸들러 직접 호출 테스트 +8건,
  `domain.notification` **분기 0% → 100%**). ⚠ 전체 커버리지는 **1%p** 만 올랐다 —
  값은 총합이 아니라 **어느 층이 비었나**에 있었다.
- **`CouponService.redeem` 분기 16%** — 남의 쿠폰·중복 사용·만료·최소주문 **가드 넷이 대부분 미실행**.
  돈과 소유권이 걸린 자리다 → **H-7 로 같은 날 메웠다**(단위 7 + 통합 3, `redeem` **0% → 100%**).
  ⚠ 열어 보니 분기 16% 가 아니라 **메서드 자체가 0%** 였다 — 패키지·클래스 평균은 *"어느 메서드가
  통째로 비었나"* 를 가린다. **총계에서 클래스로, 클래스에서 메서드로** 내려가야 한다.
- **`FileStorageService` 분기 34%** — 확장자 화이트리스트·매직바이트 검사가 *"막는지"* 미검증
  → **H-8 로 같은 날 메웠다**(검증 10건, 분기 34% → **69%**). ⚠ 여기 처음 적었던 *"`..` 경로 방어도
  미검증"* 은 **틀렸다** — 이미 덮여 있었다. 분기 % 만 보고 *"안 덮였다"* 를 판정하면 이렇게 된다.

> **세 건 다 같은 날 닫혔다.** H-5 를 깔고 → 그날 안에 H-6·H-7·H-8 을 처리해서, 커버리지 기준선이
> **명령어 90% · 분기 78%** 인 상태로 다음 날에 넘어간다(도입 시점 87%/72%).
> 도구의 값은 숫자를 올린 게 아니라 **"어디를 안 봤나" 세 곳을 정확히 짚은 것**이었다.

⚠ **그래서 게이트는 안 붙인다.** 이 숫자들의 값은 *"어디를 안 봤나"* 를 가리킨 데 있지, 올려야 할
목표가 아니다. 커버리지는 **"이 줄이 실행됐다"만 재고 "이게 맞다"는 재지 못한다** — 맞는지는
변형 주입이 잰다(WA §3). 실제로 오늘 B-15 의 가장 위험한 버그(판정 위치)는 **커버리지 100% 로도
안 잡히는** 종류였다. 사용 규약은 WA §3-2.

### MSA 인프라

- **Docker** (→ compose → **k8s는 단일 VM엔 오버, 제외**): 서비스를 실제로 쪼갤 때 도입. 현재 VM 미설치(Redis·nginx·백엔드·Oracle 전부 systemd). 설치는 sudo라 명령만 제안.
  - **컨테이너화 대상**: 백엔드(스테이트리스)부터 → Redis·nginx 순. **Oracle 19c는 컨테이너화 비추**(이미지 거대·라이선스) → 호스트/외부 유지.
  - **컨테이너 모니터링** (역할이 달라 상호보완 — 경쟁 아님):
    - ~~Docker Desktop~~ — **서버(리눅스 VM)엔 부적합**. Mac/Win 워크스테이션용 GUI. 서버는 Docker Engine 직접 사용 → 후보에서 제외.
    - **ctop** — 컨테이너용 `top`, CLI 즉석 리소스 확인. 설치 거의 0, 히스토리 없음.
    - **Portainer** — 컨테이너/스택/볼륨 관리 웹UI. 운영 편의·학습용. 메트릭 히스토리·알림은 약함.
    - **cAdvisor + node_exporter → Prometheus → Grafana** — 진짜 메트릭 수집·히스토리·알림. **위 관측 스택을 그대로 재사용**(별도 스택 불필요, 컨테이너 메트릭만 얹음).
- **이벤트 큐: RabbitMQ** (`spring-boot-starter-amqp`)
  - 용도 예: *주문 완료 → 재고 차감 · 알림 · 포인트 적립*
  - RabbitMQ 선택 이유: exchange/queue/binding 개념이 직관적, 운영 단순, 이벤트 드리븐 학습에 적합. (Kafka는 대용량 스트리밍·이벤트소싱이 필요해지면 2차 목표. Redis Streams는 부하 우려로 제외.)

#### Kafka · Kubernetes · MQTT 재검토 (2026-07-29)

세 가지 모두 **이미 결정이 있거나(Kafka·k8s) 이 도메인에 자리가 없다(MQTT).** 다시 물어봤으니 근거를 갱신해 둔다.

| 기술 | 현 결정 | 근거 · 뒤집히는 조건 |
|---|---|---|
| **Kafka** | ⏸ **2차 목표** (RabbitMQ 가 1차 — CLAUDE.md 에도 "이벤트 큐는 RabbitMQ 확정, Kafka 아님") | 둘은 **경쟁이 아니라 용도가 다르다.** 우리 이벤트는 *주문완료→알림·적립* 처럼 **작업 지시(task queue)** 라 RabbitMQ 결이다. Kafka 는 **로그·스트림을 보관하고 여러 소비자가 각자 오프셋으로 되감아 읽는** 모델이라, 값이 나오려면 ①**재처리(replay)가 필요**하거나 ②**이벤트 소싱/CDC** 를 하거나 ③초당 수천 건 스루풋이 있어야 한다. 지금 셋 다 없다.<br>→ **뒤집히는 조건**: RabbitMQ 로 먼저 쪼갠 뒤 *"그 이벤트를 다시 읽어야 한다"* 는 요구(집계 재생성·감사 재구축)가 실제로 생길 때. 순서를 지키면 **둘의 차이를 몸으로 알게 된다** — 반대로 Kafka 부터 깔면 "왜 이게 필요한가"를 영영 모른다 |
| **Kubernetes** | ⏸ **운영엔 제외 / 학습 트랙은 열어 둔다** (2026-07-29 갱신 — 원래는 §7 에 ❌ 였다) | k8s 의 값은 **여러 노드에 스케줄링·자가치유·롤링업데이트**인데, **노드가 하나면 그 전부가 무의미**하다. compose 로 되는 일을 YAML 수백 줄로 하는 셈 — **운영 근거로는 여전히 부족**.<br>→ 다만 **학습 가치는 별개이고 실재한다**(배포·헬스체크·롤링업데이트를 직접 겪는 것). 학습 트랙 규칙대로 **운영 VM 말고 별도 환경 + 경량 배포판(k3s·kind·minikube)**, **선행 조건은 Docker 화**(이미지가 없으면 올릴 게 없다).<br>⚠ 순서 주의: **compose 를 건너뛰고 k8s 로 가지 않는다.** compose 로 먼저 쪼개 봐야 "k8s 가 무엇을 대신해 주는지"가 보인다 |
| **MQTT** | ❌ **도입 안 함** (신규 판단) | MQTT 는 **저대역폭·불안정 네트워크의 IoT 디바이스**용 경량 pub/sub 프로토콜이다(센서·차량·모바일 텔레메트리). **이커머스 백오피스엔 대응하는 문제가 없다.**<br>비슷해 보이는 자리는 둘인데 **이미 답이 있다**: ①실시간 푸시 → **SSE 완료**(§6.0 인앱 알림, nginx 버퍼링까지 해결) ②서비스 간 메시징 → **RabbitMQ**(같은 브로커가 MQTT 플러그인도 지원하지만, 프로토콜을 늘릴 이유가 없다).<br>→ **뒤집히는 조건**: 실제 디바이스(키오스크·POS·배송 단말)가 붙을 때. 그 전엔 프로토콜만 늘고 얻는 게 없다 |

### 도입 조건 (언제 넣는가)

| 기술 | 도입 시점 |
|---|---|
| ~~**P6SPY** (dev SQL 로깅)~~ | ✅ **완료** 2026-07-16 — dev 프로파일 한정, plain p6spy + URL 재작성 (§6.0) |
| ~~**HTTPS** (nginx TLS 종단)~~ | ✅ **완료** 2026-07-16 — self-signed(SAN IP), 80→443 리다이렉트 (§6.0) |
| **Sentry** (에러추적) | ⏸ 보류 — 관측/MSA 단계에 관측 스택과 함께 (모노레포 단계엔 오버엔지니어링, 2026-07-16 재판단) |
| **Spring Batch** | 대량·재시작 배치 작업이 생길 때 (§6.0) |
| ApplicationEventPublisher (스프링 내부 이벤트) | ✅ **도입 시작**(2026-07-16). **3층 구조**: ①`DomainEvent`(global/messaging 마커 인터페이스, 이벤트가 implements) ②`OrderEventListener`(어댑터 — `@TransactionalEventListener` AFTER_COMMIT + **`@Async`** 수신·위임만) ③`OrderNotificationHandler`(진짜 주체 — 로직). `OrderPlacedEvent`(checkout 발행) → 리스너 → 핸들러. order는 구독자를 모름. 비동기는 `AsyncConfig`(바운드 풀). 인프로세스 @Async는 best-effort → 유실 금지는 아웃박스/RabbitMQ. **MSA 시 리스너 자리에 RabbitMQ 컨슈머, Handler는 재사용**<br>**2026-07-20 확장**: `OrderCancelledEvent`(cancel 발행, Placed와 대칭) + **`StockRunningLowEvent`** 추가. 재고 이벤트는 **catalog가 발행 주체** — 재고는 catalog 소유이고 주문 외 경로(관리자 수정 등)로 줄어도 같은 알림이 나가야 하므로. 덕분에 order는 재고 알림의 존재를 모르고 `OrderService`는 무수정(fan-out 실증: 주문 1건 → Handler 2개가 각각 `event-*` 스레드에서 반응). 임계치는 `catalog.low-stock-threshold`(기본 5, 0=품절 포함). **재고 복원은 이벤트로 빼지 않는다** — 취소 처리의 일부(동기 성공 필수)지 best-effort 후처리가 아님<br>**2026-07-27 확장**: **`StockReplenishedEvent`**(재입고, B-9/V28) — `StockRunningLowEvent` 와 대칭으로 **catalog 가 발행 주체**. 상품 **총재고 0→양수** 전환 시 발행하며 경로는 셋(주문취소·반품복원=`increaseStock`, 관리자 재고편집=`update`). 구독자는 **restock 도메인**의 `RestockEventListener`→`RestockNotificationHandler`(신청자에게 RESTOCK 알림 + 구독 소진). 재고부족(STOCK)은 notification 이 받지만 재입고는 구독 생명주기를 소유한 restock 이 받는다(catalog→restock→notification, 순환 없음). 단위가 옵션이 아니라 상품인 이유는 §5 restock 참조<br>**2026-07-20 확장 2**: **`ReviewRatingChangedEvent`**(review 발행, 작성·수정·삭제) → catalog `ReviewEventListener` → `RatingSyncHandler`가 `product.avg_rating`/`review_count` 비정규화 갱신 + `products:list` 캐시 evict. **이벤트를 쓴 이유는 성능이 아니라 순환 회피** — catalog가 review를 조회하면 기존 `review → catalog`와 합쳐져 도메인 순환이 되고 MSA 분리가 깨진다. 그래서 **집계값을 이벤트 페이로드에 실어 보낸다**(`productId`만 보내면 구독자가 review를 되물어야 해서 순환이 되살아남). 결과: 상품 목록이 조인·추가쿼리 **0회**로 별점을 읽고, 의존 방향은 `review → catalog` 한쪽뿐<br>**2026-07-31 확장 2**: **`MemberSignedUpEvent`**(G-2) — member 가 발행, 구독자는 coupon(`WelcomeCouponListener`→`WelcomeCouponHandler`, 가입 쿠폰 자동 발급). ⚠ **같은 도메인의 `MemberWithdrawnEvent` 와 처리 방식이 정반대다**: 탈퇴는 기본 `@EventListener` 라 **발행측 트랜잭션에 합류**하고(정리 실패 시 회원 삭제도 롤백돼야 한다), 가입은 `@Async`+`AFTER_COMMIT` 이다(**쿠폰 발급이 실패해도 가입은 유효**해야 한다 — 쿠폰은 다시 줄 수 있지만 가입 실패는 사용자가 다시 겪는다). 같은 이유로 **적립금 계정 생성은 이벤트로 빼지 않고** 가입 트랜잭션 안에 뒀다(배송완료 적립을 동기로 둔 것과 같은 판단). 발급 대상은 **관리자가 「가입 쿠폰」으로 지정한 쿠폰 하나**(V36 `coupon.welcome` + 함수기반 유니크 인덱스 — 지정이 없으면 기능 꺼짐). ⚠ 처음엔 설정(.env)이었는데 **바꿀 때마다 재시작**이 필요하고 무엇이 가입 쿠폰인지 화면에서 안 보여 같은 날 데이터로 옮겼다<br>**2026-07-31 확장**: **`InquiryAnsweredEvent`**(B-15) — inquiry 가 발행 주체, 구독자는 notification(`InquiryEventListener`→`InquiryNotificationHandler`). 알림 대상이 **발행자(관리자)가 아니라 이벤트에 실린 작성자**인 첫 사례라 페이로드에 `authorId`·`productId`·`inquiryTitle` 을 함께 싣는다(구독자가 inquiry 를 되물으면 `notification → inquiry` 역방향이 생긴다 — `ReviewRatingChangedEvent` 와 같은 이유). ⚠ **발행 조건이 로직의 전부**: 등록·수정 겸용 API 라 **첫 답변에서만** 발행하고, 판정은 `inquiry.answer()` **앞**에서 읽어야 한다(뒤면 항상 `ANSWERED` → 영영 발행 안 됨) |
| Spring Modulith (도메인 경계 검증) | 도메인이 늘어 경계 규칙을 테스트로 강제하고 싶을 때 |
| Docker | 첫 서비스 분리를 시작할 때 (k8s 제외, compose까지) |
| 컨테이너 모니터링 (Portainer/ctop + cAdvisor→Grafana) | Docker 전환과 세트. Docker Desktop은 서버엔 제외 |
| RabbitMQ | 서비스가 2개 이상으로 쪼개져 서비스 간 비동기 이벤트가 필요할 때 |
| Alloy + Loki + Prometheus + Grafana | 운영 관측이 필요해지는 시점 (이커머스 진입 무렵). **= LGTM 의 L·G·M 자리** — 별도 스택이 아니다 |
| 트레이싱/APM (OTel+Tempo, 대안 Pinpoint) | MSA로 서비스 간 호출 추적이 필요할 때. **= LGTM 의 T**. Datadog·Jennifer·**Dynatrace·MaxGauge 는 유료로 제외**(§관측 「상용 APM」) |
| **Mimir** (메트릭 장기저장) | Prometheus 보존기간이 모자라거나 **인스턴스가 여러 대**가 될 때. remote_write 로 나중에 갈아끼움 |
| **Kafka** | RabbitMQ 로 먼저 쪼갠 뒤 **replay·이벤트소싱·CDC** 요구가 생길 때. **학습 목적이면 RabbitMQ 를 겪은 다음**(순서를 지켜야 차이를 안다) |
| **Kubernetes** (k3s/kind) | 운영엔 제외. **학습 트랙**은 열림 — 별도 환경, **Docker compose 를 겪은 뒤** |
| **상용 APM** (Dynatrace·Jennifer·MaxGauge) | ⏸ **비용은 제약 아님**(학습용). 게이트는 **①볼 게 있는가(k6 선행) ②평가판 기간 ③평가판 제공 형태**. OSS 관측(②)을 먼저 깔고 **비교용으로 짧게** |
| **MQTT** | ❌ 도입 안 함 — 이 도메인에 **대응 문제가 없다**. 배우려면 디바이스가 있는 별도 토이 프로젝트에서 |
| OpenSearch (상품 검색 고도화) | QueryDSL/Oracle 검색이 부족해질 때 — 한글 형태소 분석·오타 보정·관련도 랭킹·패싯 집계, 대용량 상품. **로그용 아님(로그는 Loki)** |

---

## 7. 하지 않는 것 (요약)

- 이커머스 테이블 선(先)설계 ❌ — 요구사항 나올 때
- **`payment` 도메인 선설계 ❌** — PG사 미정. 주문 상태(PAID/SHIPPED)까지만 구현, 실제 PG 연동은 MSA 단계 + PG사 확정 시(§5). 지금 `pay`는 상태 전이 플레이스홀더
- 만능 board 테이블 / polymorphic FK ❌ — 도메인별 별도 테이블
- 레이어 단위 패키지 ❌ — 도메인 단위
- 모노레포 단계에서 Docker·RabbitMQ·관측 스택 설치 ❌ — 로드맵
- OpenSearch 지금 도입 ❌ — 검색은 QueryDSL+Oracle로 충분. 상품 검색 고도화 단계의 로드맵
- **Kubernetes를 운영에 ❌** — 단일 VM엔 오버엔지니어링. 운영은 Docker compose까지 (⚠ **학습 트랙은 열려 있다** — 별도 환경 + k3s/kind, §6 「학습 트랙」)
- 서버에 Docker Desktop ❌ — 워크스테이션용. 서버는 Docker Engine 직접
- **상용 APM을 "지금" ❌** — Datadog·Jennifer·Dynatrace·MaxGauge. ⚠ **유료라서가 아니다**(학습용이라 비용은 제약 아님, 2026-07-29 정정) — **볼 게 없어서**다. k6로 부하를 만들고 OSS 관측을 겪은 뒤 평가판을 짧게 태운다(§6 「상용 APM」)
- **MQTT ❌** — IoT 프로토콜이라 이커머스에 대응 문제가 없다. 실시간 푸시는 SSE(완료), 서비스 간은 RabbitMQ
- **Kafka 지금 도입 ❌** — RabbitMQ가 1차. replay·이벤트소싱 요구가 생기거나, 학습이면 RabbitMQ를 겪은 뒤 2차
- Spring Batch 지금 도입 ❌ — 대량·재시작 배치 작업 생길 때. 지금은 `@Scheduled`로 충분
