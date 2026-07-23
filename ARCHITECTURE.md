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
  └▶ catalog    상품 · 카테고리   (게시판 CRUD/검색 패턴 이식)       ✅
       └▶ cart  장바구니          (Redis 활용)                      ✅
            └▶ orders / order_item   주문 · 결제상태(PAID/SHIPPED)   ✅ 2026-07-16
                 └▶ review / inquiry  상품 리뷰 · 문의 (게시판 경험 재활용)  ✅ 2026-07-16
                 └▶ (로드맵) payment  실제 결제(PG 연동)  ⏸ MSA 단계 + PG사 확정 시
```

> **review / inquiry (2026-07-16 구현)**: 상품에 **느슨한 UUID 참조**(product_id)로 연결, polymorphic FK 없이 별도 테이블.
> 리뷰는 **구매자만**(order 도메인 `hasPurchased` 공개 API로 인증)·상품당 1회·평균별점 집계. 문의는 **비밀글 마스킹**(응답 DTO)·**ADMIN 답변**·상태(WAITING/ANSWERED).
>
> **2026-07-20 마감**: ①구매 인증 범위를 `ORDERED`만 → `ORDERED·PAID·SHIPPED`로 수정(**CANCELLED만 제외**).
> 주문 상태 확장(7/16) 때 누락돼 결제·배송 완료 고객이 리뷰를 못 쓰던 버그. 새 상태가 자동으로 포함되지
> 않도록 `<> CANCELLED`가 아닌 **명시적 열거**로 둔다. ②**포토 리뷰** — `review.image_group_id`로
> ImageGroup 재사용 구조의 두 번째 사용처(FK 없는 느슨한 UUID, `ImageService` 공개 API로만 접근).
> ③상품 목록 평균별점은 **비정규화 + 이벤트 동기화**(§6.0 이벤트 항목 참조).

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

---

## 6. 미래 인프라 로드맵

> **§6.0**은 단계 무관하게 *지금/곧* 도입 후보다. 그 아래(관측·MSA 인프라)는 **MSA/이커머스 단계** 계획으로, 모노레포 연습 단계에선 설치하지 않는다.
> (현재 인프라: Nginx · Oracle 19c · Redis 7.4.7 — systemd 서비스)

### 0. 조기 도입 후보 (단계 무관 — 지금/곧)

MSA를 기다릴 필요 없는, 개발 편의·보안·에러 가시성 개선. 현재 단일 VM·systemd에서 바로 적용 가능.

| 기술 | 판단 | 시점 | 메모 |
|---|---|---|---|
| **P6SPY** | ✅ **완료** (2026-07-16) | dev 프로파일 한정 | JDBC 가로채 실제 SQL + 바인딩값 + 실행시간 로깅. QueryDSL 동적쿼리·N+1 육안 확인용. **운영 프로파일 제외**(오버헤드·민감정보 로깅). *구현 메모*: `p6spy-spring-boot-starter`(gavlyukovskiy)는 Boot 4.1 자동설정 호환이 불확실해 채택하지 않고, **plain `p6spy` + dev 프로파일에서 datasource URL만 `jdbc:p6spy:…`로 재작성**(자동설정 무의존)했다. 운영은 순수 oracle URL이라 p6spy 코드 경로를 안 탐. 포맷/카테고리는 `spy.properties`(SLF4J·한 줄·결과셋 제외) |
| **HTTPS** | ✅ **완료** (2026-07-16) | — | JWT를 평문으로 흘리지 않기. **nginx에서 TLS 종단(:443), 백엔드는 내부 HTTP(:8080) 유지**. 내부 VM이라 **self-signed**(SAN에 IP 포함 — 브라우저는 CN 무시·SAN만 검증) 10년. **SAN: `IP:192.168.50.14, IP:127.0.0.1, DNS:localhost, DNS:glassvue.local`**(2026-07-22 재발급 — 서버 IP가 `.36`→`.14`로 바뀌어 SAN이 어긋났다. 이때 IP를 정적으로 고정했으므로 재발 없음). 원본 `/home/ecstel/nginx-tls/2026-07-22/`, 이전 인증서는 `/etc/nginx/ssl/*.bak`으로 백업. **80→443 301 리다이렉트**, 방화벽 443 개방. 프론트는 `/api` 상대경로 + JWT를 localStorage에 둬서 코드·재배포 불필요(secure 쿠키 이슈 없음). 인증서 원본 `/home/ecstel/nginx-tls/`, 배포본 `/etc/nginx/ssl/`. 공인 도메인 생기면 Let's Encrypt로 교체 |
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

### 도입 조건 (언제 넣는가)

| 기술 | 도입 시점 |
|---|---|
| ~~**P6SPY** (dev SQL 로깅)~~ | ✅ **완료** 2026-07-16 — dev 프로파일 한정, plain p6spy + URL 재작성 (§6.0) |
| ~~**HTTPS** (nginx TLS 종단)~~ | ✅ **완료** 2026-07-16 — self-signed(SAN IP), 80→443 리다이렉트 (§6.0) |
| **Sentry** (에러추적) | ⏸ 보류 — 관측/MSA 단계에 관측 스택과 함께 (모노레포 단계엔 오버엔지니어링, 2026-07-16 재판단) |
| **Spring Batch** | 대량·재시작 배치 작업이 생길 때 (§6.0) |
| ApplicationEventPublisher (스프링 내부 이벤트) | ✅ **도입 시작**(2026-07-16). **3층 구조**: ①`DomainEvent`(global/messaging 마커 인터페이스, 이벤트가 implements) ②`OrderEventListener`(어댑터 — `@TransactionalEventListener` AFTER_COMMIT + **`@Async`** 수신·위임만) ③`OrderNotificationHandler`(진짜 주체 — 로직). `OrderPlacedEvent`(checkout 발행) → 리스너 → 핸들러. order는 구독자를 모름. 비동기는 `AsyncConfig`(바운드 풀). 인프로세스 @Async는 best-effort → 유실 금지는 아웃박스/RabbitMQ. **MSA 시 리스너 자리에 RabbitMQ 컨슈머, Handler는 재사용**<br>**2026-07-20 확장**: `OrderCancelledEvent`(cancel 발행, Placed와 대칭) + **`StockRunningLowEvent`** 추가. 재고 이벤트는 **catalog가 발행 주체** — 재고는 catalog 소유이고 주문 외 경로(관리자 수정 등)로 줄어도 같은 알림이 나가야 하므로. 덕분에 order는 재고 알림의 존재를 모르고 `OrderService`는 무수정(fan-out 실증: 주문 1건 → Handler 2개가 각각 `event-*` 스레드에서 반응). 임계치는 `catalog.low-stock-threshold`(기본 5, 0=품절 포함). **재고 복원은 이벤트로 빼지 않는다** — 취소 처리의 일부(동기 성공 필수)지 best-effort 후처리가 아님<br>**2026-07-20 확장 2**: **`ReviewRatingChangedEvent`**(review 발행, 작성·수정·삭제) → catalog `ReviewEventListener` → `RatingSyncHandler`가 `product.avg_rating`/`review_count` 비정규화 갱신 + `products:list` 캐시 evict. **이벤트를 쓴 이유는 성능이 아니라 순환 회피** — catalog가 review를 조회하면 기존 `review → catalog`와 합쳐져 도메인 순환이 되고 MSA 분리가 깨진다. 그래서 **집계값을 이벤트 페이로드에 실어 보낸다**(`productId`만 보내면 구독자가 review를 되물어야 해서 순환이 되살아남). 결과: 상품 목록이 조인·추가쿼리 **0회**로 별점을 읽고, 의존 방향은 `review → catalog` 한쪽뿐 |
| Spring Modulith (도메인 경계 검증) | 도메인이 늘어 경계 규칙을 테스트로 강제하고 싶을 때 |
| Docker | 첫 서비스 분리를 시작할 때 (k8s 제외, compose까지) |
| 컨테이너 모니터링 (Portainer/ctop + cAdvisor→Grafana) | Docker 전환과 세트. Docker Desktop은 서버엔 제외 |
| RabbitMQ | 서비스가 2개 이상으로 쪼개져 서비스 간 비동기 이벤트가 필요할 때 |
| Alloy + Loki + Prometheus + Grafana | 운영 관측이 필요해지는 시점 (이커머스 진입 무렵) |
| 트레이싱/APM (OTel+Tempo, 대안 Pinpoint) | MSA로 서비스 간 호출 추적이 필요할 때. Datadog·Jennifer는 유료로 제외 |
| OpenSearch (상품 검색 고도화) | QueryDSL/Oracle 검색이 부족해질 때 — 한글 형태소 분석·오타 보정·관련도 랭킹·패싯 집계, 대용량 상품. **로그용 아님(로그는 Loki)** |

---

## 7. 하지 않는 것 (요약)

- 이커머스 테이블 선(先)설계 ❌ — 요구사항 나올 때
- **`payment` 도메인 선설계 ❌** — PG사 미정. 주문 상태(PAID/SHIPPED)까지만 구현, 실제 PG 연동은 MSA 단계 + PG사 확정 시(§5). 지금 `pay`는 상태 전이 플레이스홀더
- 만능 board 테이블 / polymorphic FK ❌ — 도메인별 별도 테이블
- 레이어 단위 패키지 ❌ — 도메인 단위
- 모노레포 단계에서 Docker·RabbitMQ·관측 스택 설치 ❌ — 로드맵
- OpenSearch 지금 도입 ❌ — 검색은 QueryDSL+Oracle로 충분. 상품 검색 고도화 단계의 로드맵
- Kubernetes ❌ — 단일 VM엔 오버엔지니어링. Docker compose까지만
- 서버에 Docker Desktop ❌ — 워크스테이션용. 서버는 Docker Engine 직접
- Datadog·Jennifer ❌ — 상용/유료. 에러추적은 Sentry, 트레이싱은 OTel+Tempo/Pinpoint
- Spring Batch 지금 도입 ❌ — 대량·재시작 배치 작업 생길 때. 지금은 `@Scheduled`로 충분
