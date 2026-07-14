# VuePractice

Vue 3와 Spring Boot로 만드는 연습용 프로젝트. Claude Code와 함께 진행한다.

**목표** — Vue를 처음 써보면서, 실제 운영에 가까운 스택(Oracle · Redis · Nginx)을 한 번씩 다 거쳐보는 것.
**주제** — 사내 공지 게시판 (목록·검색·작성·조회수)

---

## 기술 스택

### 프론트엔드 (`esp-frontend/`)

| 기술 | 버전 | 용도 |
|---|---|---|
| Vue | 3.3.0 | UI 프레임워크 |
| rsbuild | 1.7.3 | 번들러 (dev 서버 · 빌드) |
| DevExtreme | 23.1.15 | DataGrid · Form 등 UI 컴포넌트 |
| Tailwind CSS | 4.0.14 | 레이아웃 · 스타일 |
| pnpm | 10 | 패키지 매니저 |

### 백엔드 (`esp-backend/`)

| 기술 | 버전 | 용도 |
|---|---|---|
| Spring Boot | 4.1.0 | 웹 · DI |
| Java | 25 (Oracle JDK) | 런타임 |
| Gradle | 9.5.1 (wrapper) | 빌드 |
| Spring Data JPA / Hibernate | 7.x | ORM |
| Oracle JDBC | ojdbc11 | DB 드라이버 |
| Spring Data Redis (Lettuce) | — | 캐시 · 세션 |
| Lombok | — | 보일러플레이트 제거 |

### 인프라

| 기술 | 버전 | 용도 |
|---|---|---|
| Oracle Database | 19c | 주 데이터 저장소 |
| Redis | 7.4.7 | 캐시 · 조회수 카운터 · (예정) 세션 |
| Nginx | 1.27.1 | 정적 파일 서빙 · API 리버스 프록시 |

> Redis와 Nginx는 소스 빌드해서 systemd 서비스로 등록해 두었다.

---

## 구조

```
work/
├─ esp-backend/     Spring Boot (REST API)
├─ esp-frontend/    Vue 3 SPA
└─ CLAUDE.md        Claude Code용 프로젝트 규칙 (버전 고정 등)
```

요청 흐름:

```
브라우저 → Nginx(:80) ─┬─ /            → 정적 파일 (Vue 빌드 결과)
                       └─ /api/*       → Spring Boot(:8080) ─┬─ Oracle
                                                             └─ Redis
```

개발 중에는 Nginx를 거치지 않고 rsbuild dev 서버(`:3000`)를 쓴다. `/api` 요청은 자동으로 `:8080`으로 프록시된다.

---

## 실행 방법

### 사전 준비

- Node.js 24.13.0 (nvm), pnpm 10
- JDK 25
- 접근 가능한 Oracle DB, 로컬 Redis(`127.0.0.1:6379`)

### 환경변수

저장소 루트에 `.env` 파일을 만든다. **커밋하지 않는다** (`.gitignore`에 포함).

```dotenv
DB_HOST=<oracle 호스트>
DB_PORT=<리스너 포트>
DB_SERVICE=<서비스명 또는 PDB명>
DB_USER=<계정>
DB_PASSWORD=<비밀번호>
```

### 백엔드

```bash
cd esp-backend
set -a; . ../.env; set +a      # .env를 환경변수로 주입
./gradlew bootRun              # http://localhost:8080
```

동작 확인:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"} → Oracle · Redis 연결까지 정상
```

### 프론트엔드

```bash
cd esp-frontend
pnpm install
pnpm dev                       # http://localhost:3000
```

### 프로덕션 빌드

```bash
# 백엔드 → 실행 가능한 jar
cd esp-backend && ./gradlew bootJar

# 프론트 → 정적 파일 (Nginx가 서빙)
cd esp-frontend && pnpm build
```

---

## 설정 메모

- **`ddl-auto=update`** — 연습 단계라 엔티티 기준으로 테이블을 자동 생성한다. 스키마가 안정되면 `validate` + Flyway로 옮길 것. (운영에서는 `update`를 쓰지 않는다)
- **Redis `allkeys-lru`** — 메모리가 차면 오래된 키부터 삭제된다. 캐시·세션 용도이므로 지워지면 안 되는 데이터는 넣지 말 것.
- **비밀값** — 코드나 `application.properties`에 직접 쓰지 않고 `.env` 환경변수로만 주입한다.

---

## 진행 계획

- [x] 환경 구축 (Node · JDK · Redis · Nginx · Oracle 연동)
- [x] 백엔드 · 프론트엔드 골격
- [ ] 게시판 — 엔티티 · 목록 API · Swagger(springdoc)
- [ ] 게시판 — Vue + DevExtreme DataGrid 연동
- [ ] QueryDSL 동적 검색 (제목 · 작성자 · 기간)
- [ ] Redis — 조회수 카운터 · 목록 캐시
- [ ] 로그인 (Spring Session + Redis)
