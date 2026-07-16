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

> **CQRS 범위**: 여기서의 CQRS는 *서비스 계층을 command/query로 분리*하는 경량(application-level) CQRS다.
> 읽기/쓰기 모델을 물리적으로 분리하고 이벤트로 동기화하는 풀 CQRS는 트래픽이 커지는 이커머스 단계의 선택지로 남겨둔다.
> 조회 측은 QueryDSL로 엔티티를 거치지 않고 DTO를 바로 projection 한다.

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
member          회원 · 인증 (게시판 5단계 로그인이 시작점)
  └▶ catalog    상품 · 카테고리   (게시판 CRUD/검색 패턴 이식)
       └▶ cart  장바구니          (Redis 활용)
            └▶ orders / order_item   주문 · 결제
                 └▶ review / inquiry  상품 리뷰 · 문의 (게시판 경험 재활용)
```

---

## 6. 미래 인프라 로드맵

> **§6.0**은 단계 무관하게 *지금/곧* 도입 후보다. 그 아래(관측·MSA 인프라)는 **MSA/이커머스 단계** 계획으로, 모노레포 연습 단계에선 설치하지 않는다.
> (현재 인프라: Nginx · Oracle 19c · Redis 7.4.7 — systemd 서비스)

### 0. 조기 도입 후보 (단계 무관 — 지금/곧)

MSA를 기다릴 필요 없는, 개발 편의·보안·에러 가시성 개선. 현재 단일 VM·systemd에서 바로 적용 가능.

| 기술 | 판단 | 시점 | 메모 |
|---|---|---|---|
| **P6SPY** | ✅ **완료** (2026-07-16) | dev 프로파일 한정 | JDBC 가로채 실제 SQL + 바인딩값 + 실행시간 로깅. QueryDSL 동적쿼리·N+1 육안 확인용. **운영 프로파일 제외**(오버헤드·민감정보 로깅). *구현 메모*: `p6spy-spring-boot-starter`(gavlyukovskiy)는 Boot 4.1 자동설정 호환이 불확실해 채택하지 않고, **plain `p6spy` + dev 프로파일에서 datasource URL만 `jdbc:p6spy:…`로 재작성**(자동설정 무의존)했다. 운영은 순수 oracle URL이라 p6spy 코드 경로를 안 탐. 포맷/카테고리는 `spy.properties`(SLF4J·한 줄·결과셋 제외) |
| **HTTPS** | ✅ 도입 | 곧 | JWT를 평문으로 흘리지 않기. **nginx에서 TLS 종단**, 백엔드는 내부 HTTP 유지. 내부 VM이라 Let's Encrypt(공인 도메인) 대신 self-signed 또는 mkcert. 프론트 API base·secure 쿠키 플래그 함께 점검 |
| **Sentry** (에러추적) | ✅ 조기 도입 | 관측 스택보다 먼저 가능 | 예외/에러 그루핑·릴리스 추적, **프론트(Vue)+백(Spring) 동시 커버**. 셀프호스트/무료티어로 가볍게. 메트릭·로그·트레이스와 별개의 "에러 전용" 도구 |
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
| **HTTPS** (nginx TLS 종단) | **곧** (§6.0) |
| **Sentry** (에러추적) | 조기 — 관측 스택 전에도 가능 (§6.0) |
| **Spring Batch** | 대량·재시작 배치 작업이 생길 때 (§6.0) |
| ApplicationEventPublisher (스프링 내부 이벤트) | **지금부터** — 모노레포 단계의 이벤트는 이걸로. 나중에 큐로 바꾸기 자연스러움 |
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
- 만능 board 테이블 / polymorphic FK ❌ — 도메인별 별도 테이블
- 레이어 단위 패키지 ❌ — 도메인 단위
- 모노레포 단계에서 Docker·RabbitMQ·관측 스택 설치 ❌ — 로드맵
- OpenSearch 지금 도입 ❌ — 검색은 QueryDSL+Oracle로 충분. 상품 검색 고도화 단계의 로드맵
- Kubernetes ❌ — 단일 VM엔 오버엔지니어링. Docker compose까지만
- 서버에 Docker Desktop ❌ — 워크스테이션용. 서버는 Docker Engine 직접
- Datadog·Jennifer ❌ — 상용/유료. 에러추적은 Sentry, 트레이싱은 OTel+Tempo/Pinpoint
- Spring Batch 지금 도입 ❌ — 대량·재시작 배치 작업 생길 때. 지금은 `@Scheduled`로 충분
