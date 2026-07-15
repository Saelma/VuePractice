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
