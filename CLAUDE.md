# 환경 (반드시 이 버전으로 고정)

- OS: Rocky Linux 9 (dnf 기반)
- Node.js: 24.13.0  (nvm로 설치, 시스템 패키지 금지)
- pnpm: v10  (corepack로 활성화, npm/yarn 사용 금지)
- JDK: 25 GA (Oracle, /opt/java/jdk-25)
- Redis: 7.4.7  (소스 빌드)
- Nginx: 1.27.1 (소스 빌드)
- Vue: 3.3.0 / rsbuild: 1.7.3 / devextreme: 23.1.15 / tailwindcss: 4.0.14
- Oracle DB: 19c @ (DB_HOST, 계정은 .env 참고)

# 규칙
- sudo가 필요한 명령은 실행하지 말고 "제안"만 할 것. 내가 직접 확인 후 실행한다.
- 위 버전과 다른 걸 설치하려 하지 말 것.

# 작업 절차 (코드 규칙과 별개 — 반드시 먼저 읽을 것)
- **작업 시작 전 `docs/WORKING-AGREEMENTS.md`를 읽는다.** 마이그레이션·권한·검증·배포·핸드오프 작성에
  관한 합의가 거기 있고, 각 항목이 어떤 사고에서 나왔는지도 함께 적혀 있다.
- 작업 후보를 뽑을 때는 **최신 핸드오프 하나만 보지 않는다.** 최소 2일치 + ARCHITECTURE.md를 대조할 것
  (직전 문서에서 조용히 빠진 이월 항목을 놓친다). 각 핸드오프 끝의 `## 이월` 절이 1차 근거다.
  날짜별 핸드오프는 **`docs/handoffs/YYYY-MM-DD-handoff.md`** 에 있다.
- **기능을 새로 만들 차례면 `docs/BACKLOG.md`를 본다.** 이커머스 기준 무엇이 비었는지와 우선순위 근거가
  거기 있다. 이월(그날 작업의 잔여)과 백로그(제품 기능 후보)는 수명이 달라 자리를 나눠 뒀다.
  항목을 끝내면 백로그의 「완료」로 옮긴다.
- **뭔가 안 될 때는 `docs/TROUBLESHOOTING.md`를 먼저 본다.** 「증상 → 어디를 의심하나 → 진단 명령」
  색인이다(빌드·테스트 / DB·마이그레이션 / 배포·운영 / 알림·SSE). **증상은 아는데 원인을 모를 때**
  여는 문서라, 날짜로 찾는 핸드오프로는 못 찾는 것들이 여기 모인다.
- 새 규칙·교훈이 생기면 날짜별 핸드오프에만 적지 말고 옮긴다 — **어디로 옮기는지가 정해져 있다**:
  - *"앞으로 이렇게 일한다"* (규약) → `docs/WORKING-AGREEMENTS.md`
  - *"이 증상이면 여길 의심한다"* (진단) → `docs/TROUBLESHOOTING.md`
  - *"그날 무슨 일이 있었다"* (경위·실측값) → 핸드오프에 **그대로 두고 나머지는 링크만** 한다.
  ⚠ 같은 사실을 두 곳에 적으면 한쪽만 고쳐져 어긋난다. **원본은 핸드오프 하나뿐이다.**

# 아키텍처 규칙 (코드 작성 시 반드시 따를 것)
- 패키지는 domain/ + global/로 나눈다. 업무 도메인은 domain.<도메인>(member·notice·coupon·project…), 공통·인프라(common·response·exception·config·security·redis·messaging·log)는 global 아래. controller/service/repository를 최상위에 두는 레이어 구조 금지 — MSA 전환 시 domain 하위를 폴더째 분리 가능해야 함.
- 메시징 패키지는 구현체에 묶지 않고 global/messaging 으로 둔다 (이벤트 큐는 RabbitMQ 확정, Kafka 아님).
- PK는 UUIDv7 + Oracle RAW(16). 앱에서 생성(uuid-creator 등, 시간순 v7). Long/SEQUENCE/IDENTITY 금지, 문자열(VARCHAR2) 저장 금지.
- 모든 엔티티는 BaseTimeEntity를 상속한다 (createdAt/updatedAt 자동).
- 테이블·컬럼은 snake_case 단수형. Oracle 예약어 회피(주문 테이블은 orders).
- 의존성 주입은 생성자 주입만 사용한다 (@RequiredArgsConstructor + private final). 필드 주입(@Autowired 필드) 금지.
- 컨트롤러는 인터페이스(XxxController) + 구현(XxxControllerImpl)로 분리한다. Swagger 애노테이션(@Tag/@Operation 등)은 인터페이스에 둔다.
- 서비스는 경량 CQRS로 XxxCommandService(조작) · XxxQueryService(조회)로 나눈다. 조회 서비스는 @Transactional(readOnly=true).
- DTO는 Java record로 만든다.
- 로깅은 SLF4J(@Slf4j)만 쓴다. System.out/printStackTrace 금지.
- 모든 응답은 공통 래퍼 ApiResponse<T> — 성공 {success:true,data}, 에러 {success:false,error:{code,message}}. HTTP 상태코드는 유지(에러도 4xx/5xx).
- 예외는 ErrorCode(enum 카탈로그) + BusinessException + @RestControllerAdvice 전역 처리. 컨트롤러/서비스에서 try-catch로 응답을 만들지 않는다.
- 도메인 간 직접 참조 금지 — 공개 인터페이스나 이벤트로만 통신한다. 내부 구현체(~Impl 등)는 가시성을 최소화한다.
- 모노레포 단계에선 서비스 간 이벤트를 스프링 ApplicationEventPublisher로 처리한다. Docker·RabbitMQ·관측 스택(Alloy/Loki/Prometheus/Grafana)은 MSA 단계 로드맵이며 지금 설치하지 않는다.
- 설계 배경·ERD·미래 로드맵은 ARCHITECTURE.md를 참조/갱신한다.
- **화면(프론트엔드)을 만들거나 고칠 때는 `DESIGN.md`(디자인 시스템 — 토큰·타이포·레이아웃·컴포넌트 패턴·DevExtreme 공존·화면별 방침·레퍼런스)를 따르고 갱신한다.** 화면에서 임의 색·간격을 쓰지 말고 **토큰만** 쓴다. 강조색은 CTA·상태에만(무채색 원칙). 새 패턴·토큰이 생기면 DESIGN.md에 반영한다.
