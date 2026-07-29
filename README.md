# Glassvue

Vue 3 + Spring Boot 로 만드는 **연습용 이커머스**. Claude Code 와 함께 진행한다.

**목표** — Vue 를 처음 써보면서, 실제 운영에 가까운 스택(Oracle · Redis · Nginx)을 한 번씩 다 거쳐보는 것.
**주제** — 상품 카탈로그 · 장바구니 · 주문 · 리뷰 · 쿠폰 · 적립금 · 관리자 도구를 갖춘 단일 브랜드 커머스.

> 게시판(공지)에서 출발해 이커머스로 확장했다. 공지는 지금도 `notice` 도메인으로 남아 있다.

**문서 지도** — 이 파일은 *어떻게 띄우는가*만 다룬다.
설계·도메인 경계·인프라 로드맵은 [`ARCHITECTURE.md`](./ARCHITECTURE.md),
화면 규칙(토큰·컴포넌트·레퍼런스)은 [`DESIGN.md`](./DESIGN.md),
일하는 방식·사고 기록은 [`docs/WORKING-AGREEMENTS.md`](./docs/WORKING-AGREEMENTS.md),
기능 후보는 [`docs/BACKLOG.md`](./docs/BACKLOG.md), 날짜별 작업 기록은 [`docs/handoffs/`](./docs/handoffs/).

---

## 기술 스택

### 프론트엔드 (`glassvue-frontend/`)

| 기술 | 버전 | 용도 |
|---|---|---|
| Vue | 3.3 | UI 프레임워크 (Composition API) |
| Vue Router | 4 | 라우팅 + 인증/권한 가드 |
| rsbuild | 1.7 | 번들러 (dev 서버 · 빌드) |
| DevExtreme | 23.1 | 관리자 화면 DataGrid |
| Tailwind CSS | 4 | 레이아웃 · 스타일 (CSS-first `@theme` 토큰) |
| Vitest | — | 단위 테스트 |
| pnpm | 10 | 패키지 매니저 |

> 상태 관리 라이브러리는 쓰지 않는다 — `reactive` 기반의 얇은 스토어(`src/stores/`)로 충분하다.

### 백엔드 (`glassvue-backend/`)

| 기술 | 버전 | 용도 |
|---|---|---|
| Spring Boot | 4.1 | 웹 · DI |
| Java | 25 (Oracle JDK) | 런타임 |
| Gradle | 9.5 (wrapper) | 빌드 |
| Spring Data JPA / Hibernate | 7.x | ORM |
| QueryDSL | 5.1 | 동적 검색 (상품 필터 등) |
| Flyway | — | 스키마 버전 관리 (V1~V33) |
| Spring Security + JJWT | 0.12 | JWT 인증 · 역할 기반 인가 |
| Spring Data Redis (Lettuce) | — | 캐시 · 카운터 · 토큰 저장 |
| springdoc-openapi | 3.0 | Swagger UI |
| uuid-creator | 6.0 | UUIDv7 PK 생성 |
| scrimage | 4.3 | 이미지 파생본(WebP 썸네일) |
| p6spy | 3.9 | dev 프로파일 SQL 로깅 |

### 인프라

| 기술 | 버전 | 용도 |
|---|---|---|
| Oracle Database | 19c | 주 데이터 저장소 |
| Redis | 7.4.7 | 캐시 · 조회수 카운터 · 토큰 |
| Nginx | 1.27.1 | 정적 파일 서빙 · API 리버스 프록시 · TLS 종단 |

> Redis 와 Nginx 는 소스 빌드해 systemd 서비스로 등록했다. 서버 설정 원본은 [`infra/`](./infra/) 에 있고,
> 서버는 그 사본이다(`scripts/check-infra-drift.sh` 로 대조).

---

## 구조

```
.
├─ glassvue-backend/     Spring Boot (REST API)
│  └─ src/main/java/com/glassvue/
│     ├─ domain/         업무 도메인 — MSA 분리 단위
│     │                  (member · auth · catalog · cart · order · coupon · point ·
│     │                   review · inquiry · notice · notification · restock ·
│     │                   wishlist · image · audit)
│     └─ global/         공통·인프라 (security · config · exception · response ·
│                        messaging · policy · querydsl · storage · common)
├─ glassvue-frontend/    Vue 3 SPA
├─ docs/                 상시 문서(WORKING-AGREEMENTS · BACKLOG) + handoffs/
├─ infra/                nginx · systemd 설정 원본
└─ scripts/              배포 · 드리프트 검사
```

**패키지는 레이어가 아니라 도메인으로 나눈다** — `domain/<도메인>` 을 폴더째 들어내면 하나의 서비스가
되도록. 자세한 근거는 `ARCHITECTURE.md §1`.

요청 흐름:

```
브라우저 → Nginx(:443 TLS) ─┬─ /        → 정적 파일 (Vue 빌드 결과)
                            └─ /api/*   → Spring Boot(:8080) ─┬─ Oracle
                                                              └─ Redis
```

- `:80` 은 `:443` 으로 301 리다이렉트. 내부 VM 이라 인증서는 self-signed(SAN 에 IP 포함).
- 개발 중에는 Nginx 를 거치지 않고 rsbuild dev 서버(`:3000`)를 쓴다. `/api` 는 `:8080` 으로 프록시된다.
- 알림은 SSE(`/api/notifications/stream`) — nginx 에서 이 경로만 버퍼링을 끈다.

---

## 실행 방법

### 사전 준비

- Node.js 24.13.0 (nvm), pnpm 10
- JDK 25
- 접근 가능한 Oracle DB, 로컬 Redis(`127.0.0.1:6379`)

### 환경변수

`.env` 파일을 만든다. **커밋하지 않는다**(`.gitignore` 에 포함).
키 목록과 형식은 [`infra/env.example`](./infra/env.example) 을 참고한다 — 값은 비어 있다.

```dotenv
DB_HOST=<oracle 호스트>
DB_PORT=<리스너 포트>
DB_SERVICE=<서비스명 또는 PDB명>
DB_USER=<계정>
DB_PASSWORD=<비밀번호>
JWT_SECRET=<256bit 이상 랜덤값 — openssl rand -base64 48>
```

> ⚠ **테스트를 돌리기 전에도 반드시 소싱**한다. 안 하면 DB 통합 테스트가 **조용히 skip 되고
> `BUILD SUCCESSFUL` 로 끝난다**(실제로 겪은 사고 — `docs/WORKING-AGREEMENTS.md §3`).

### 백엔드

```bash
cd glassvue-backend
set -a; . /path/to/.env; set +a   # .env를 환경변수로 주입
./gradlew bootRun                 # http://localhost:8080
```

동작 확인:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"} → Oracle · Redis 연결까지 정상
```

API 문서: `http://localhost:8080/swagger-ui.html`

### 프론트엔드

```bash
cd glassvue-frontend
pnpm install
pnpm dev                       # http://localhost:3000
```

### 메일 흐름을 브라우저로 보기 (비밀번호 재설정 · 이메일 인증)

⚠ **운영(`:8080`)은 메일을 보내지 않는다** — `application.yml` 에 `spring.mail` 키가 없어
`JavaMailSender` 빈이 아예 없다. 메일이 나가는 건 **dev 프로파일뿐**이다.

```bash
# 1) 메일 캐처 확인(부팅 시 자동 기동 — 꺼져 있으면 systemctl start mailpit)
systemctl is-active mailpit && curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8025/

# 2) dev 백엔드를 운영과 겹치지 않는 포트로 (운영은 그대로 둔다)
cd glassvue-backend
set -a; . /path/to/.env; set +a
./gradlew bootRun --args="--server.port=8084 --spring.profiles.active=dev"

# 3) 프론트 dev 서버를 그 백엔드로 붙인다
cd glassvue-frontend
API_TARGET=http://127.0.0.1:8084 pnpm dev     # http://localhost:3000
```

그 뒤 브라우저에서 **`http://localhost:3000`** 으로 흐름을 밟고, 나간 메일은
**`http://127.0.0.1:8025`**(Mailpit 웹 UI)에서 본다.

> ⚠ **서버 밖(개발 PC)에서 볼 때**는 Mailpit 이 루프백에만 바인딩돼 있어 바로 못 연다. SSH 로 끌어온다:
> ```bash
> ssh -L 8025:127.0.0.1:8025 -L 3000:127.0.0.1:3000 ecstel@<서버IP>
> ```
> 이후 개발 PC 브라우저에서 `http://localhost:3000`(화면)·`http://localhost:8025`(메일함).

### 테스트

```bash
# 백엔드 — .env 소싱 후에 돌린다(위 경고 참고)
cd glassvue-backend && ./gradlew test

# 프론트
cd glassvue-frontend && pnpm test
```

### 프로덕션 빌드

```bash
# 백엔드 → 실행 가능한 jar
cd glassvue-backend && ./gradlew bootJar

# 프론트 → 정적 파일 (Nginx가 서빙)
cd glassvue-frontend && pnpm build
```

---

## 설정 메모

- **`ddl-auto=validate` + Flyway** — 스키마는 `src/main/resources/db/migration/` 의 버전 스크립트가
  소유한다. 엔티티를 고치면 **마이그레이션이 세트로 따라온다**(안 쓰면 앱이 아예 안 뜬다).
  절차·주의사항은 그 디렉터리의 `README.md`.
- **PK 는 UUIDv7 + Oracle `RAW(16)`** — 앱에서 생성한다(시간순 정렬 가능). 시퀀스·IDENTITY 를 쓰지 않는다.
- **Redis 용도** — 목록 캐시(`products:list`·`notices:list`), 조회수 카운터(`notice:view:`),
  JWT refresh 토큰(`auth:refresh:`)·블랙리스트(`auth:blacklist:`)·비밀번호 재설정 토큰(`auth:reset:`).
  ⚠ `allkeys-lru` 라 메모리가 차면 오래된 키부터 삭제된다 — **지워지면 안 되는 데이터는 넣지 않는다.**
- **인증은 JWT** (세션 아님) — access 30분 / refresh 14일, 로그아웃 시 access 를 블랙리스트에 올린다.
- **비밀값** — 코드나 `application.yml` 에 직접 쓰지 않고 `.env` 환경변수로만 주입한다.

---

## 만들어진 것

커머스 한 바퀴가 돌아간다 — **상품(옵션·재고) → 장바구니 → 주문 → 배송 추적 → 반품**.

| 갈래 | 내용 |
|---|---|
| 카탈로그 | 카테고리 · 상품 옵션(variant)별 재고 · 이미지(WebP 파생본) · 정가/할인가 · 검색·필터·정렬 |
| 주문 | 장바구니 · 주문서 · 배송지 주소록 · 배송비 정책 · 운송장 추적 · 반품(적립금 환불) |
| 혜택 | 쿠폰(정액/정률) · 적립금 · 회원 등급(누적 구매액 기준 적립률) |
| 참여 | 리뷰(별점·포토) · 상품 문의 · 위시리스트 · 재입고 알림 |
| 알림 | 인앱 알림 SSE(벨·토스트) + 타입별 on/off |
| 계정 | 가입·로그인(JWT) · 비밀번호 변경/재설정 · 이메일 · 탈퇴 |
| 관리자 | 상품·카테고리·주문·회원·쿠폰 관리, 매출 통계, 감사 로그(최상위 관리자 전용) |

권한은 `USER` / `ADMIN` / `SUPER_ADMIN` 3계층이다.

> 아직 **가짜인 것**: 결제(`pay` 는 상태 전이 플레이스홀더 — PG 미연동), 메일·SMS 발송(채널 없음),
> 배송 추적(목업 화면). 각각의 도입 조건은 `docs/BACKLOG.md` D 절에 있다.

---

## 진행 계획

다음에 무엇을 만들지는 [`docs/BACKLOG.md`](./docs/BACKLOG.md) 가 소유한다(완료 항목도 근거와 함께 거기 남는다).
인프라·관측 도구의 도입 시점은 [`ARCHITECTURE.md §6`](./ARCHITECTURE.md).
