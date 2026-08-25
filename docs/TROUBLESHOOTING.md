# 트러블슈팅 (Troubleshooting)

> **이 문서가 답하는 질문은 하나다 — 「이 증상을 보면 어디를 의심하나」.**
> 증상은 알지만 원인은 모르는 상태에서 여는 문서라, **증상으로 찾을 수 있게** 색인을 앞에 둔다.

## 문서 자리 나누기 (⚠ 안 지키면 갈린다)

| 문서 | 답하는 질문 | 예 |
|---|---|---|
| **이 문서** | *"이 증상이면 어디를 의심하나"* | *"테스트가 무더기로 컨텍스트 로딩 실패한다"* |
| `WORKING-AGREEMENTS.md` | *"앞으로 이렇게 일한다"* | *"변형 주입 전에 `git status` 를 본다"* |
| `handoffs/YYYY-MM-DD-handoff.md` | *"그날 무슨 일이 있었다"* | 경위·실측값·판단 근거 **(원본)** |

→ **사실의 원본은 핸드오프에 둔다.** 이 문서는 **증상 → 의심할 곳 → 진단 명령**만 갖고 나머지는 **링크**한다.
같은 사실을 두 곳에 적으면 한쪽만 고쳐져 어긋난다(2026-08-04 B-17 이 재고 이력에 사유를 복사하지 않은 것과 같은 규칙).

### ⚠ **막힌 뒤에만 여는 문서가 아니다** (2026-08-10)

> **사고**: 새 엔드포인트가 배포됐는지 확인하려고 비로그인 요청의 **401** 을 근거로 쓰려 했다.
> §1 배포·운영 표에 *"미인증 요청이 401 → 「엔드포인트 있음」으로 판단했다"* 가 **2026-08-05 부터
> 있었는데** 펴 보지 않았다. 결과적으로 옳은 수단(jar 내용물)에 닿았지만, 그건 그날 아침에 이미
> **같은 모양으로 두 번 데어** 의심하는 상태였기 때문이지 이 문서 덕이 아니다.

이 표는 **두 종류**가 섞여 있다:
- *"막혔다 → 어디를 보나"* — **사후**에 여는 항목(대부분).
- 🔴 *"이 신호를 근거로 쓰지 마라"* — **사전**에 읽어야 값이 있는 항목.
  `401`·`0건`·`BUILD SUCCESSFUL`·`systemctl is-active` 처럼 **답처럼 보이는 값**들이다.

→ **확인 수단을 고를 때** 한 번 편다. 「무엇으로 판정할까」를 정하는 순간이 이 문서를 볼 때다 —
   판정하고 나서 열면 이미 틀린 근거로 적어 놓은 뒤다.

---

## 1. 증상 색인

**빌드·테스트**

| 증상 | 의심 | 어디 |
|---|---|---|
| `BUILD SUCCESSFUL` 인데 실제로 아무것도 안 돌았다 | `.env` 미소싱(통합 테스트가 `@EnabledIfEnvironmentVariable(named="DB_HOST")` 라 **조용히 SKIPPED**) · `Task :test UP-TO-DATE`(환경변수는 Gradle 입력이 아니다) | WA §3 — 판정은 `tests`·`skipped` **숫자로만**. `set -a; . /home/ecstel/work/.env; set +a` + `cleanTest` + `python3 -c "import glob,xml.etree.ElementTree as ET; print(sum(int(ET.parse(p).getroot().get('skipped')) for p in glob.glob('build/test-results/test/*.xml')))"` · 🔴 **곁증거 하나**(2026-08-14): 새 마이그레이션을 넣었는데 `flyway_schema_history` 최신 버전이 그대로면 **컨텍스트가 안 떴다는 뜻**이다 — V51 이 그렇게 드러났다 · `handoffs/2026-08-14-handoff.md` §11-4 |
| 통합 테스트가 **무더기로** 컨텍스트 로딩 실패 | 코드가 아니라 **인프라 접속**(DB IP·기동 여부). `Caused by` 최하단까지 내려가면 `ORA-12170` 이 보인다 | WA §5 |
| 테스트가 **간헐적으로** 실패 | 운영 프로세스가 같은 Redis 를 건드린다(조회수 플러셔가 테스트 키까지 SCAN+GETDEL) | WA §3 — 정리가 아니라 **격리**(`spring.data.redis.database`) |
| 로그인 실패를 쓰는 테스트가 어느 날 429 | MockMvc 는 IP 가 전부 `127.0.0.1` → 시도 제한 카운터를 공유 | WA §3 — `X-Real-IP` 로 자기 IP 를 준다 |
| 변형을 넣었는데 **하나도 안 빨개진다** | 테스트가 그 경로에 못 닿았거나, 그 규칙이 코드에 아예 없다. ⚠ **화면 테스트라면 방어가 두 겹인지 본다**(2026-08-14): 버튼에 `:disabled` 가 걸려 있으면 `trigger('click')` 이 **두 번째부터 아예 안 나가서**, JS 가드(핸들러의 이른 return)를 지워도 초록이다 — 테스트가 가드가 아니라 **`disabled` 속성**을 보고 있었던 것이다(WA §2-4-1 동치 변형의 UI 판) | WA §3 · 가르는 법: **`await` 를 빼고 같은 틱에 연달아** 트리거한다. Vue 는 비동기로 렌더하므로 `disabled` 가 DOM 에 붙기 **전에** 두 번째가 도착한다 — **실제 브라우저의 빠른 연타가 그 모양**이라 인위적인 상황이 아니다 · `handoffs/2026-08-14-handoff.md` §10-4 |
| 변형을 넣었더니 **테스트가 아니라 워커가 죽는다** | 운영 코드가 한 겹 방어뿐(가드 하나에만 안전이 걸려 있다) | WA §3 · `handoffs/2026-08-05-handoff.md` §6-3 |
| **카운트 쪽만** 망가뜨린 변형이 안 잡힌다 | 테스트가 `content[*]` 만 보고 **총건수를 안 본다**. 게다가 결과가 한 페이지에 들어가면 count 쿼리가 **아예 안 돈다** | WA §3 — 결과 수 > `size` 로 만들고 `totalElements` 를 단언 · `handoffs/2026-08-06-handoff.md` §4-5 |
| **권한 규칙을 지웠는데** 테스트가 통과한다 | 버그가 아니라 **동치 변형**일 수 있다 — 컨트롤러가 `@LoginUser`(required=true)로 받으면 리졸버가 **같은 401** 을 낸다. 매처가 빠져도 안 열리는 경로가 있다 | WA §2-4-1 — 소유자를 **어디서 받나**로 가른다 · `handoffs/2026-08-07-handoff.md` §5-5 |
| 전수 테스트가 `NoSuchFileException: build/test-results/test/binary/in-progress-results-generic.bin` 로 깨진다(테스트는 다 돈 뒤, XML 은 **0개**) | **같은 `build/` 를 쓰는 Gradle 실행이 하나 더 있다** — 한쪽의 `cleanTest`·`test` 가 다른 쪽의 진행 중 결과 디렉터리를 지운다. ⚠ 실패가 **끝에서** 나서 코드·환경 문제로 보이고, 재실행해도 같은 자리에서 또 깨진다 | 돌리기 **전에** 센다: `pgrep -af "java.*[G]radleWorkerMain"` (0 이어야 한다). 데몬(`ps -ef \| grep Gradle`)이 여럿인 것은 정상 — **워커**가 있으면 남의 테스트가 도는 중이다. 🔴 **패턴을 대괄호 없이 쓰면 안 된다**(2026-08-12 실측): `pgrep -af GradleWorkerMain` 은 **그 문자열을 담은 자기 명령줄까지** 세어 워커가 0인데 **2로 답한다** — 그래서 «남의 테스트가 돈다» 로 오진한다. 같은 이유로 `while pgrep -f GradleWorkerMain; do …` 는 **영원히 안 끝난다**(자기 자신을 본다). 기다리려면 `while pgrep -f "java.*[G]radleWorkerMain" >/dev/null; do sleep 15; done` · `handoffs/2026-08-12-handoff.md` §7 |
| 새 enum 값을 넣었는데 **단위 테스트는 다 초록**인데 운영에서 `ORA-02290` | **CHECK 제약을 안 넓혔다**(Oracle enum CHECK 트랩). 🔴 **목(mock)은 제약을 모른다** — 단위 테스트로는 절대 안 잡힌다. ⚠ 통합 테스트를 써도 `@Transactional` 롤백만 하고 끝나면 **INSERT 가 DB 에 안 닿아 제약이 한 번도 실행되지 않는다** | `V<n>` 마이그레이션으로 CHECK 를 함께 넓히고, **통합 테스트에서 `entityManager.flush()` 를 명시**해 실제로 INSERT 를 밀어 넣는다 · `ProductSoftDeleteIntegrationTest` ⑤절 · `OrderReturnIntegrationTest` 감사 절 · `handoffs/2026-08-14-handoff.md` §11-5 |
| DB 제약을 걸었는데 **도는지 확인이 안 된다** | 앱 방어(엔티티·서비스)가 먼저 잡아 제약이 **한 번도 실행되지 않는다** | WA §2-4-2 — `esptest` 에서 직접 INSERT · **거부와 통과를 함께** 본다 |
| 화면 테스트가 **단독 실행은 초록인데 전수에서만** 깨진다 | **고정 대기**(`setTimeout(0)`·`await nextTick` 한 번)로 비동기 위젯을 기다리고 있다 — 다른 파일과 함께 돌면 그만큼 더 걸린다(2026-08-14, DevExtreme DataGrid 첫 로딩) | 고정 대기를 **조건 대기**로 바꾼다: `await vi.waitUntil(() => w.find('.dx-data-row').exists(), { timeout: 5000, interval: 20 })` · `handoffs/2026-08-14-handoff.md` §10-5 |
| 프론트 전수 끝에 **`TypeError: window.getComputedStyle is not a function`** 이 「처리되지 않은 에러」로 뜬다 | 🔴 **원인이 둘이고 하나는 언마운트로 안 잡힌다.** ①**마운트한 컴포넌트를 언마운트하지 않았다** — 위젯이 뒤에서 계속 도는데 jsdom 이 먼저 정리된다. ②**DevExtreme 의 테마 폴링**(`ui/themes.js`) — «테마 CSS 가 다 붙었나» 를 10ms 간격으로 **최대 15초** 확인하는데, 확인 방법이 `.dx-theme-marker` 의 `getComputedStyle(...).fontFamily` 라 **jsdom 에선 영원히 못 찾는다.** ⚠ 이건 위젯이 아니라 **모듈**이 돌리는 타이머라 **마운트한 걸 다 정리해도 남는다.** 둘 다 **테스트는 전부 초록인 채로 에러만 따로 뜬다** | ① `afterEach(() => wrapper?.unmount())` ② 스택에 `readThemeMarker`·`isPendingThemeLoaded` 가 보이면 그쪽이다 → setup 파일에서 마커가 찾을 CSS 를 심는다(`src/test/setup-devextreme.js`). 한 번 읽히면 인터벌이 끊긴다 · `handoffs/2026-08-14-handoff.md` §10-5·§10-6 |
| DevExtreme 화면 테스트가 **느리다**(한 건에 몇 초, 전수에서 `testTimeout` 을 넘긴다) | 위와 **같은 원인**이다 — 테마 폴링 15초가 그리드 렌더를 붙잡는다. 🔴 로직이 느린 게 아니다 | `src/test/setup-devextreme.js` 를 `setupFiles` 에 둔다. 2026-08-14 실측: 감사 이력 화면 6건이 전수에서 **15.3초 → 3.8초**. ⚠ 그리고 **`vi.waitUntil` 의 timeout 을 `testTimeout` 과 같게 두지 말 것** — 조건 대기가 자기 메시지를 낼 기회가 없어 «타임아웃» 만 뜨고 **무엇을 기다렸는지 안 나온다**(안 12초 / 밖 20초) · `handoffs/2026-08-14-handoff.md` §10-6 |
| **DevExtreme 화면을 테스트할 수 있나** | 🔴 **된다 — 스텁이 필요 없다**(2026-08-14 실측). `dx-datagrid` 가 서고 `dx-data-row` 가 그려지며 `calculate-display-value` 결과까지 `w.text()` 로 읽힌다. ⚠ **단 `attachTo` 를 쓰면 안 된다** — `app.onUnmount is not a function` 으로 죽는다(@vue/test-utils 2.4.11 ↔ vue 3.3.0, DevExtreme 과 무관). ⚠ **경계**: 그리드는 되지만 **팝업 위젯(SelectBox 드롭다운)은 안 열린다** — `.dx-list-item` 이 0개다 | `views/AuditLogAdminView.test.js` 가 첫 사례다. ⚠ **`setupFiles` 로 테마 폴링을 재우는 것이 전제다**(아래 두 줄) · `handoffs/2026-08-14-handoff.md` §10-5·§10-6 |
| 전수가 **어제까지 초록이던 자리에서** 빨개졌다(내 변경과 무관해 보인다) | 공유 espdb 라 **어제 남긴 검증 잔재가 오늘 테스트에 보인다.** 🔴 **여기서 갈래가 둘이고 대응이 반대다**: ①**테스트의 정의가 틀렸다** — 운영 데이터는 그걸 «드러냈을 뿐» 이다 → **고친다** ②**테스트의 전제가 운영에 깨졌다**(«그 상태가 없다» 를 가정했다) → **견디게 만든다**. ⚠ 둘을 같은 손짓으로 넘기면 ①이 영영 안 고쳐진다 — *"또 운영 데이터네"* 로 지나간다 | 가르는 법: **그 테스트가 무엇을 묻는 이름인지** 본다. *"코드와 SQL 이 같은 정의인가"* 류면 ①을 먼저 의심하고 **서비스 쪽 조건과 대조 쿼리를 한 줄씩 맞춰 본다**. 2026-08-14 실측: 저재고 대조 SQL 에 `deleted_at IS NULL` 이 없어(F-7 을 08-12 에 놓쳤다) **정의가 다른 채로 초록**이었고, 「삭제 대기이면서 저재고」인 행이 운영에 처음 생긴 날 드러났다. 같은 날 ②도 함께 났다(어제 검증 쿠폰의 발급 창이 겹쳐 **생성 자체가 거부**) · `handoffs/2026-08-14-handoff.md` §6 |

**DB · 마이그레이션**

| 증상 | 의심 | 어디 |
|---|---|---|
| 한글이 몇 자 안 들어가고 `ORA-12899` | `VARCHAR2(n)` 이 **바이트**(이 DB 는 `NLS_LENGTH_SEMANTICS=BYTE`) | WA §2-2-1 — 항상 `n CHAR` |
| enum 값을 추가했더니 `ORA-02290` | Hibernate 가 만든 CHECK 제약을 `ddl-auto=update` 가 못 고친다 | `handoffs/2026-07-16-handoff.md` · BACKLOG(B-15 주석) |
| 값을 읽을 때 `ORA-18716` | 감사 컬럼이 plain `TIMESTAMP` 다 — `validate` 는 통과하고 **읽을 때** 터진다 | `handoffs/2026-07-24-handoff.md` (V26 사고) |
| 빈 DB 에서만 마이그레이션 실패 | V1 이 baseline 시점 스키마가 아니다 | WA §2-2 — `esptest` 로 상시 검증 |
| 새 컬럼을 넣은 뒤 **옛 행에서만** 조작이 400/거부로 막힌다(새로 만든 것은 멀쩡하다) | 🔴 **`DEFAULT` 가 기존 행에 대해 거짓말을 한다.** 제약은 안 어기는데 **뜻이 틀린** 경우다 — «얼마나 빠졌나» 류는 0 이 사실이지만, **«무엇을 요청했나 · 어떤 상태인가» 류는 0/NULL 이 «없다» 인지 «모른다» 인지 갈린다.** ⚠ **테스트로는 안 나온다** — 테스트가 만드는 행은 전부 **새 코드로** 만들어져서 «마이그레이션 이전부터 그 상태이던 행» 이 존재할 수 없다 | 운영 데이터를 **세어 본다**: 그 컬럼이 뜻을 갖는 상태의 행을 뽑아 값이 말이 되는지 눈으로 본다. 보정은 **새 `V<n>`** 으로(적용된 스크립트는 수정 금지). 🔴 **보정 조건을 좁힌다** — «주문 전체가 0» 처럼 **새 방식과 구분되는** 조건이어야 새 데이터를 덮지 않는다 · `handoffs/2026-08-25-handoff.md` §5-2 (V58→V59) |

**배포 · 운영**

| 증상 | 의심 | 어디 |
|---|---|---|
| 배포했는데 **옛 코드**가 나갔다(스크립트는 정상 종료) | `main` ff 머지를 안 했다 — 스크립트는 **main 워크트리**에서 빌드한다 | WA §5 — `check-deploy-branch.sh` |
| 배포했는데 **화면이 그대로** | ①브라우저가 옛 JS 번들을 캐시 · 🔴 ②**프론트를 아예 안 올렸다** — 백엔드·프론트는 **스크립트가 둘**이라 «배포했다» 가 반쪽일 수 있다(2026-08-20 실측: 백엔드만 올라간 사이 일반 회원에게 「새 공지」 버튼이 계속 보였다) | WA §5 — ①API 응답 ②배포된 번들 ③하드 새로고침 순으로 가른다. **번들을 직접 뜯는 게 가장 확실하다**: `H=$(curl -sk https://localhost/ \| grep -o 'index\.[a-f0-9]*\.js'); curl -sk "https://localhost/static/js/$H"` 를 받아 **로컬 `dist` 의 파일명과 대조**(해시가 같으면 그 빌드가 서빙 중이다) · `handoffs/2026-08-20-handoff.md` §11-5 |
| 🔴 **번들 안을 뒤졌는데 «없다» 가 나온다** — 옛 코드라 없는 건지 확신이 안 선다 | **빌드가 이름을 뭉갠다.** `<script setup>` 의 지역 변수·함수(`canManage` 같은 것)는 minify 돼 **새 번들에도 없다** — «없다» 가 두 뜻이다 | **살아남는 이름만 근거로 쓴다**: 객체 **키**(`requiresAdmin:`·`meta:`)·**문자열 리터럴**(화면 문구·라우트 `name`). 2026-08-20 에 `canManage` 부재를 근거로 썼다가 «minify 돼서 없다» 와 «옛 코드라 없다» 를 못 갈랐다(판정이 맞았던 건 함께 본 `requiresAuth` 덕) · WA §3-3-2 · `handoffs/2026-08-20-handoff.md` §11-5 |
| 화면은 **막는데 API 는 열려 있다**(또는 그 반대) | 화면과 서버를 **함께 좁히는 변경**인데 배포가 반쪽이다 | 🔴 **좁히는 변경은 서버를 먼저 올린다** — 반대면 «버튼은 사라졌는데 API 는 열린 채» 가 된다. ⚠ 프론트가 따로 배포되는 구조라 이 창은 **앞으로도 생긴다** · `handoffs/2026-08-20-handoff.md` §11-5 |
| 미인증 요청이 401 → "엔드포인트 있음"으로 판단했다 | **없는 경로도** Security 필터에서 401 이 난다 | WA §5 — OpenAPI·번들 해시 같은 **확실한 신호**를 쓴다. ⚠ **배포된 jar 안을 직접 보는 게 가장 싸다**: `unzip -l <jar> \| grep <새 클래스\|새 마이그레이션>` (2026-08-10 실측) |
| 로그를 뒤졌는데 **0건**이라 "안 밟혔다"고 판단했다 | **재부팅이 저널을 지웠다.** 이 VM 의 journald 는 휘발성이라 **현재 부팅분만** 남는다 — 날짜가 같아도 갈린다 | WA §3-3-1 — 판정 **전에** `uptime -s`. 대상 시각이 그보다 앞이면 그 0 은 무효다. ⚠ 돈·재고는 **원장(DB)** 으로 본다(`point_history`·`stock_history`) · `handoffs/2026-08-10-handoff.md` §6 |
| 취소·반품했는데 **재고가 일부만** 복원됐다 | 그 주문이 가리키는 **옵션(`product_variant`)이 이미 삭제**됐다. `increaseStock` 은 조용히 넘어가고 **이력도 안 남긴다**(재고가 안 변했는데 남기면 원장이 거짓이 된다) — **설계다** | 옵션 생사를 먼저 본다: `select i.product_name, v.id from order_item i left join product_variant v on v.id=i.variant_id where i.order_id=…` · `handoffs/2026-08-10-handoff.md` §9-2 |
| `systemctl is-active` 는 `active` 인데 안 된다 | 프로세스는 살아 있고 기능은 죽었다 | WA §5 — `/actuator/health` 로 본다 |
| 재부팅 뒤 백엔드가 `deactivating (stop-sigterm)` · `Result: timeout` 인데 **로그엔 `Started … in 129 seconds` 가 찍혀 있다** | 🔴 **기동에 성공했는데 systemd 가 결승선 직전에 죽였다.** `TimeoutStartSec=120` 인데 **콜드 부팅 첫 기동이 129초** 걸린다(같은 VM 의 Oracle 이 함께 뜨느라 느리다). `Restart=on-failure` 로 재시도하면 캐시가 더워져 **57초**에 뜨므로 **혼자 낫는다** — 그래서 «가끔 뜨는데 가끔 실패» 로 보인다 | ⚠ **«실패» 로 기록되지만 원인은 앱이 아니다.** 판별: `journalctl -u glassvue-backend \| grep "Started GlassvueBackendApplication in"` 로 **소요 초를 본다** — 120 을 넘겼으면 이 건이다. 항구 대책은 `TimeoutStartSec` 를 늘리는 것(§3-7) · `handoffs/2026-08-21-handoff.md` §6 |
| 🔴 **`.git/index` 가 0바이트** · `fatal: 현재 브랜치가 망가진 것처럼 보입니다` · object 파일 몇 개가 0바이트 | **크래시·강제 재부팅이 git 쓰기를 잘랐다.** ⚠ **커밋 «내용» 은 거의 안 깨진다** — 깨지는 건 마지막에 쓰인 것들(index · loose ref · 그 커밋의 object 몇 개)이다. 🔴 **push 는 성공했는데 로컬만 날아간 상태일 수 있다**(원격이 로컬보다 앞선다) | 복구 순서는 §5-1. **먼저 `git ls-remote origin` 과 `.git/logs/HEAD`(reflog)를 본다** — 둘 다 크래시에 잘 살아남고, 되돌아갈 SHA 를 알려 준다 · `handoffs/2026-08-21-handoff.md` §6 |
| 메일이 안 온다 | **운영은 발송이 꺼져 있다**(`spring.mail` 키 없음) — 그게 정상 | WA §3 · `README.md` |
| 배포 종료 로그에 `NoClassDefFoundError` 무더기 | 구 프로세스 밑에서 jar 를 갈아치웠다 | **§2-3 (아래)** |
| 서비스가 부팅에만 실패한다(손으로는 됨) | SELinux 컨텍스트(`init_t` vs `unconfined_t`) | **§3 (아래)** |
| 디스크가 차서 **VM 가상 디스크를 늘렸는데 게스트가 그대로** | **스냅샷**이 있으면 VM 은 차등 디스크 위에서 돈다 — base 를 `--resize` 하면 **명령은 성공하고 게스트는 안 커진다** | **§4 (아래)** |
| `du` 합계가 `df` 와 **크게 어긋난다** | 읽을 권한이 없는 디렉터리를 **0 으로 세고 있다**(`/opt/oracle/oradata` 는 `oracle:oinstall drwxr-x---`) | **§4 (아래)** — 차이를 반올림으로 넘기면 «지울 게 더 있다» 는 틀린 결론이 나온다 |

**관리 화면**

| 증상 | 의심 | 어디 |
|---|---|---|
| 필터에서 **「전체」를 고르면 목록이 빈다** | 「전체」의 값이 `null` 이 아니라 문자열(`'ALL'` 등)이다 — 서버가 enum·Boolean 변환에 실패해 **400** 인데 화면엔 *"자료가 없다"* 로 보인다 | ⚠ **세 번 나왔다**(주문 `?status` · 리뷰 `?hidden` · 문의 `?status`). 「전체」는 **파라미터를 빼는 값**이어야 한다 |
| 필터에서 **`false` 를 골랐는데 전체가 나온다** | 클라이언트가 falsy 를 걸러 버렸다(`hidden \|\| undefined`) — `false` 와 「안 보냄」은 **다른 상태**다 | `api/review.js` 주석 · `handoffs/2026-08-04-handoff.md` |
| 관리자 목록인데 **비밀글 본문이 「🔒」로 나온다** | 고객용 응답 DTO 를 재사용했다 — 마스킹은 «볼 권한이 없는 사람» 규칙이라 관리자 화면에 그대로 쓰면 **답을 쓰라면서 질문을 가린다** | `AdminInquiryResponse` 주석 · `handoffs/2026-08-06-handoff.md` §4-1 |
| **상품이 목록·검색에서 사라졌다**(지운 적 없는데) | 뜻이 **둘**이라 그것부터 가른다(2026-08-12, F-7): ①**상태가 `HIDDEN`**(숨김 — 계속 팔 생각이 있는 상태) ②**삭제 대기**(`deleted_at` 이 찍혔다 — 시한이 있다). ⚠ **상세 URL 을 직접 치면 갈린다**: 숨김은 열리고 **삭제 대기는 404** 다. ⚠ 장바구니에 담겨 있었다면 **줄은 남고 「판매하지 않는 상품」으로 뜬다**(삭제 대기여도 줄을 안 지운다 — 복구를 위해서다) | `/admin/products/trash` 에 있으면 삭제 대기다(**남은 기간**과 **누가 지웠나**가 함께 보인다). 거기서 **복구**하면 목록·검색·장바구니가 함께 돌아온다. ⚠ 유예(기본 `catalog.purge-grace-days`, 7일)가 지나면 **배치가 영구 삭제**하고 그때는 되돌릴 수 없다 · `handoffs/2026-08-12-handoff.md` §14 |
| 목록에서 **상품명 칸이 비어 있다** | 뜻이 **둘**이라 그것부터 가른다: ①상품이 지워졌다(느슨한 참조라 문의·리뷰는 함께 안 지워진다) ②애초에 상품이 없는 **일반 문의**다. 유형 열을 함께 보면 갈린다 | `InquiryAdminView.productText` · `handoffs/2026-08-07-handoff.md` §5-1. ⚠ **리뷰 관리는 답이 다르다**(2026-08-12): 리뷰는 상품 문의뿐이라 이름이 빌 이유가 삭제 하나뿐이고, **서버가 `productDeleted` 로 답한다** — 화면에서 다시 판정하지 않는다. 문구는 두 화면이 `constants/labels.js` 의 `DELETED_PRODUCT` 하나를 쓴다 |

**알림 · SSE**

| 증상 | 의심 | 어디 |
|---|---|---|
| `/api/notifications/stream` 이 401 만 되풀이 | 만료된 토큰으로 재연결 중 — 스트림이 갱신 경로를 안 지난다 | **§2-1 (아래)** |
| 로그에 `AsyncRequestNotUsableException` + `Broken pipe` | SSE 가 끊긴 뒤라 정상이다 — 전용 핸들러가 조용히 흘린다 | ARCHITECTURE 「예외 처리」 · `handoffs/2026-08-04-handoff.md` §3-7 |
| **알림은 오는데 누르면 깨진 페이지**로 간다 | 링크를 **문자열로 조립**하는데 그 재료가 `null` 이다(`/products/null#…`). ⚠ **서버 로그에 아무것도 안 남는다** — 틀린 게 코드 경로가 아니라 문자열이라 예외도 에러도 없다 | 링크 생성부에 **갈래**가 있는지 본다 · `handoffs/2026-08-07-handoff.md` §2-2 |
| 앵커로 들어왔는데 **엉뚱한 위치**에 선다 | 위쪽 섹션이 나중에 렌더돼 목표를 밀어냈다 — 스크롤은 시작 시점 위치에서 멈춘다 | WA §2-9 · `composables/useAnchorScroll.js` |

---

## 2. 상세 — 2026-08-05 에 판 것

### 2-1. `/api/notifications/stream` 이 401 만 되풀이한다 (31초 간격)

**증상**: 로그인해 둔 탭을 방치하면 알림이 안 온다. nginx 에 `stream` 이 **401 만** 쌓인다
(실측 2026-08-04: 하루 **200 7건 · 401 108건**). 사용자에게 보이는 에러는 없다.

**원인**: 알림 SSE 는 `EventSource` 가 `Authorization` 헤더를 못 실어서 **생 `fetch`** 로 연다.
그래서 `client.js` 의 `request()` 를 안 지나가고, 거기 있는 **401 → 토큰 갱신 경로를 통째로 비켜간다.**
백오프(1초→상한 30초)도 갱신 로직도 **이미 있었다** — 스트림만 그 길을 안 쓴 것이다.
⚠ 관측되는 **31초 간격이 곧 백오프 상한**이다.

**왜 오래 안 보였나**: 코드 주석이 *"이 사이 REST 호출이 refresh 하고 다음 연결이 성공한다"* 고
**다른 요청에 기대고** 있었다. 화면을 쓰는 동안엔 실제로 그렇게 낫는다 — **가만히 둔 탭에서만** 깨진다.

**진단**
```bash
grep 'notifications/stream' /var/log/nginx/glassvue-access.log | grep "$(date +%d/%b/%Y)" \
  | awk '{print $9}' | sort | uniq -c        # 401 만 늘고 200 이 안 붙으면 이 건이다
```
**고친 곳**: `stores/notifications.js`(401 이면 스스로 갱신) · `api/client.js`(`refreshSession` 공개).
**근거**: `handoffs/2026-08-05-handoff.md` §6.

### 2-2. API 오타 하나가 500 + `ERROR` 를 만든다

**증상**: `/api/zzz` 처럼 없는 경로가 **500**, 로그에 `ERROR "Unhandled exception"`.
`DELETE /api/notices` 처럼 **메서드만 틀려도** 같다.

**원인**: 포괄 `Exception` 핸들러가 `NoResourceFoundException`·`HttpRequestMethodNotSupportedException`
을 받아 버렸다. **클라이언트 잘못이 서버 오류로 보이고**, 배포 확인의 「`ERROR` 0건」이 오염된다.

⚠ **왜 오래 안 보였나**: `/api/products/오타` 는 `{id}` 패턴에 걸려 **400 이 잘 나온다.**
500 이 되는 건 **어느 패턴에도 안 걸릴 때뿐**이라, 몇 번 찔러 보고 *"에러 처리 잘 되네"* 로 끝난다.

**진단**
```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/zzz          # 404 여야 한다
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE http://127.0.0.1:8080/api/notices  # 405
```
**고친 곳**: `GlobalExceptionHandler`(전용 핸들러 둘) · `ErrorCode`(`COMMON-404`·`COMMON-405`).
**근거**: `handoffs/2026-08-05-handoff.md` §2 · ARCHITECTURE 「예외 처리 · 응답 포맷」.

### 2-3. 배포할 때마다 종료가 깨진다 (`NoClassDefFoundError`)

**증상**: 배포 직후 저널에
`Failed to stop bean 'webServerGracefulShutdown'` / `'webServerStartStop'` / `'redisConnectionFactory'`
와 `NoClassDefFoundError`. **서비스는 정상 기동하므로 아무도 안 본다.**

**원인**: 배포 스크립트가 **구 프로세스가 살아 있는 채로** `cp -f` 로 jar 를 덮어썼다.
JVM 은 클래스를 **필요할 때** 읽으므로, **종료 경로에서만 쓰는 클래스**를 이미 바뀐 파일에서 찾다 실패한다.
→ 톰캣 정지도 Redis 연결 정리도 건너뛰어지고, 연결이 곱게 닫히지 않는다.

**진단** (배포 직후)
```bash
journalctl -u glassvue-backend --since "10 min ago" | grep -c NoClassDefFoundError   # 0 이어야 한다
journalctl -u glassvue-backend --since "10 min ago" | grep -c 'Failed to stop bean'  # 0
# 양성 증거까지 본다 — 없어야 할 게 없는 것만으로는 부족하다(WA §3-3)
journalctl -u glassvue-backend --since "10 min ago" | grep -E 'Graceful shutdown|HikariPool.*Shutdown'
```
**고친 곳**: `scripts/deploy-backend.sh` — **`stop` → `cp` → `start`** 로 순서를 바꿨다(복사 실패 시 `.bak` 복구 갈래 포함).
⚠ **부수효과**: 이제 종료가 활성 요청을 **실제로 기다린다** — SSE 탭이 붙어 있으면 최대 30초.
고장이 아니라 되찾은 동작이다.
**근거**: `handoffs/2026-08-05-handoff.md` §5.

### 2-4. 어제 로그를 다시 보려는데 없다

**증상**: `journalctl --since yesterday` 가 빈손. `--list-boots` 에 **현재 부팅 하나**뿐.

**원인**: 이 VM 의 저널이 **휘발성**이다. 재부팅하면 이전 로그가 통째로 사라진다.

→ **로그로 판정했으면 건수·시각·예외 이름을 그 자리에서 핸드오프에 옮겨 적는다**(WA §3-3-1).
→ 배포 전후 대조처럼 **대조군이 필요한 작업은 같은 부팅 안에서** 끝낸다.

---

## 3. 서비스가 부팅에 안 뜰 때

> **사고 (2026-07-23)**: Oracle 자동기동이 재부팅마다 실패해 **운영이 하루에 두 번 내려갔다.**
> 원인을 찾기까지 **가설 4개를 헛짚었고**, 진짜 원인(SELinux)은 그 4개 중 어디에도 없었다.
> 아래는 그 하루에서 뽑은 것들이다.

### 3-1. "지금 떠 있다"는 자동기동 성공의 증거가 아니다

프로세스가 돌고 있어도 **누가 손으로 올렸을 수 있다.** 확인하지 않으면 고장을 정상으로 기록한다.

> 아침에 `ora_pmon` 이 돌고 백엔드도 `active` 라 자동기동이 된 줄 알았는데, 사용자가 부팅 후
> `lsnrctl start` + `startup` 을 직접 한 것이었다. 서비스 상태는 `failed` 인데 프로세스는 살아 있는,
> **어긋난 상태**였다.

→ 자동기동을 검증할 땐 **재부팅 후 아무것도 손대기 전에** 상태를 본다. 손대는 순간 그 부팅은 못 쓴다.

### 3-2. **수동은 되는데 서비스로만 실패하면 SELinux를 의심한다**

셸에서 실행하면 `unconfined_t`, systemd 가 실행하면 `init_t` — **컨텍스트가 다르다.**
그래서 "손으로 하면 되는데 부팅만 안 된다"가 나온다.

> `init_t → su_exec_t`(`/bin/su`) 실행이 거부돼, `su` 로 데몬을 띄우는 SysV 스크립트가 통째로 실패했다.
> Oracle 이 **실행조차 안 됐으므로** TNS/ORA 에러도 리스너 로그도 어디에도 없었다.

- **AVC 거부는 `journalctl` 에 없다.** `/var/log/audit/audit.log` 로만 간다 —
  `journalctl | grep avc` 는 빈손이라 "SELinux 는 아니네"로 **잘못 배제하기 쉽다.**
  ```bash
  sudo ausearch -ts <시작시각> -te <끝시각>     # 실패 시각 전후를 통째로
  sudo ausearch -m avc -ts recent
  ```
- 저널의 `Starting SETroubleshoot daemon for processing new SELinux denial logs...` 는
  **거부가 발생했다는 신호**다(그 데몬은 거부가 있을 때 깨어난다).
- init 스크립트에서 계정을 바꿀 땐 `su`(`su_exec_t`) 말고 **`runuser`(`bin_t`)** 를 쓴다 —
  util-linux 가 init 스크립트용으로 제공하는 대체품이고 옵션(`-s`/`-c`)이 같다.

#### 3-2-1. **서비스로 띄울 실행 파일을 홈 디렉터리에 두지 않는다** (2026-07-29)

같은 원리의 다른 얼굴이다. 홈 아래 파일은 `user_home_t` 라벨이라 **systemd(`init_t`)가 실행 자체를 못 한다.**

> **사고 (2026-07-29)**: Mailpit 을 `~/tools/mailpit/` 에 두고 유닛을 올렸더니
> **`status=203/EXEC` + "Failed to locate executable … Permission denied"**. 파일 권한은 `-rwxr-xr-x`,
> 소유자도 맞고, **셸에서는 잘 실행됐다**(`unconfined_t` 라서). §3-2 가 말한 "손으로는 되는데
> 서비스만 실패"가 그대로 재현된 것이다.

→ **`/opt` 아래에 둔다**(`usr_t`). 운영 jar(`/opt/glassvue-backend`)와 자리도 일관된다.
→ 진단은 **`ls -lZ`** 한 줄이면 갈린다 — 컨텍스트가 `user_home_t` 면 이 문제다.
   (`203/EXEC` 는 "실행조차 못 했다"는 뜻이라 애플리케이션 로그가 아예 없다 — 프로그램 문제로 착각하기 쉽다.)
→ 옮긴 뒤 **`restorecon`** 으로 라벨을 확정한다. `chcon` 은 재라벨링 때 날아간다.

### 3-3. `systemctl start` 로 부팅 조건을 재현한다 (재부팅 불필요)

`systemctl start` 는 ExecStart 를 **`init_t` 로 실행**한다 — SELinux 조건이 부팅과 같다.
서비스를 내린 뒤 `systemctl start` 가 성공하면 부팅에서도 성공한다.

→ 재부팅은 비싸다(운영 중단 + 한 번에 한 가설). **먼저 이걸로 반복 검증**하고, 재부팅은 마지막 확인에만 쓴다.

### 3-4. 출력을 버리는 스크립트는 원인을 숨긴다 — 사본에서 벗기고 돌린다

벤더 init 스크립트는 흔히 `> /dev/null 2>&1` 로 출력을 버린다. 그러면 `"Failed to start ..."`
**한 줄만** 남아 아무것도 알 수 없다. **원본은 건드리지 말고 사본에서 리다이렉트만 벗긴다.**

```bash
sudo sed 's#> /dev/null 2>&1##' /etc/init.d/<유닛> > /tmp/debug.sh
sudo bash /tmp/debug.sh start
```

⚠ 그 스크립트들은 보통 "이미 떠 있으면 아무것도 안 함"으로 빠져나간다(`pmon` 검사 등).
**반드시 내린 상태(cold)에서 돌려야** 진짜 경로를 탄다. 유닛이 `failed` 면 `systemctl stop` 이
**아무 일도 안 하므로**(systemd 가 비활성으로 보고 `ExecStop` 을 건너뜀) 사본의 `stop` 으로 직접 내린다.

### 3-5. 벤더 스크립트는 고치지 말고 drop-in 으로 덮는다

설치 프로그램이 만든 파일은 재설치·패치에 덮어써진다. `/etc/systemd/system/<유닛>.d/override.conf` 로
필요한 것만 덮는다. 스크립트가 `if [ -z "$VAR" ]` 같은 훅을 두고 있으면 `Environment=` 로 갈아끼울 수 있다.

> 실제로 `SU` 훅 하나로 `su` → `runuser` 교체가 끝났다. 원본은 한 글자도 안 건드렸다.

### 3-6. "고쳤는데 그대로 실패" 는 가설이 틀렸다는 신호다

수정이 **적용됐는지**와 **효과가 있었는지**를 갈라서 본다. 적용은 됐는데 증상이 같으면
**원인 가설이 틀린 것**이지, 적용이 덜 된 게 아니다.

> 네트워크 순서를 고치자 시작 시각이 **부팅 후 13초 → 39초**로 밀렸다. 즉 수정은 **분명히 먹었다.**
> 그런데 증상이 똑같았다 — 그 사실 자체가 "네트워크 타이밍이 원인"을 반증하고 있었는데,
> 한동안 "override 가 안 먹었나"를 의심하며 시간을 썼다.

→ 실패가 **얼마나 빨리** 나는지도 단서다. Oracle 이 1초 만에 "실패"하면 **실행조차 안 된 것**이고
(정상 기동은 30초 걸렸다), 그건 설정 문제가 아니라 **실행 자체가 막힌 것**을 가리킨다.


---

### 3-7. 🔴 **기동 «성공» 이 «타임아웃 실패» 로 기록되는 자리** (2026-08-21)

> **사고 (2026-08-21 14:46 재부팅)**: 백엔드가 `deactivating (stop-sigterm) (Result: timeout)` 이었는데
> 저널에는 **`Started GlassvueBackendApplication in 129.298 seconds`** 가 찍혀 있었다.
> **앱은 떴다. 다만 9초 늦었다.** `TimeoutStartSec=120` 이라 systemd 가 그 직전에 SIGTERM 을 보냈다.

⚠ **이 실패는 «혼자 낫는다» 는 것이 함정이다.** `Restart=on-failure` 로 5초 뒤 재시도하면 페이지
캐시가 더워져 **57.352초**에 뜬다 — 즉 **재부팅할 때마다 한 번 실패하고 두 번째에 산다.**
🔴 그래서 «가끔 안 뜬다» 로 기억되고, 아침에 보면 멀쩡해서 **원인을 안 찾게 된다.**

**왜 콜드 부팅만 느린가**: 같은 VM 에서 **Oracle 이 함께 뜬다**(유닛의 `After=oracledb_ESPDB-19c.service`).
DB 가 아직 몸을 푸는 동안 커넥션 풀·Flyway 검증·JPA 스키마 검증이 전부 그 위에서 기다린다.

- [ ] **판별은 «소요 초» 하나로 끝난다**:
      `journalctl -u glassvue-backend | grep "Started GlassvueBackendApplication in"`
      120 을 넘겼으면 이 건이다. ⚠ **`systemctl status` 만 보면 절대 안 보인다** — 거기엔
      «timeout» 만 있고 «몇 초 걸렸나» 가 없다.
- [ ] ⚠ **앱을 의심하기 전에 이걸 먼저 본다.** 2026-08-21 에는 디스크·OOM·메모리를 먼저 팠는데
      **셋 다 정상**이었다(디스크 52% · OOM 없음). 시간을 쓴 순서가 틀렸다.
- [ ] 🔴 **항구 대책은 `TimeoutStartSec` 를 늘리는 것**이다(제안 — sudo 라 사용자가 적용):
      `/etc/systemd/system/glassvue-backend.service` 의 `TimeoutStartSec=120` → **`300`**.
      ⚠ **`ExecStartPost` 헬스체크 자체는 그대로 둔다** — 그게 «active = 요청 처리 준비 완료» 를
      만드는 장치라, 줄이면 안 된다. 늘려야 하는 건 **기다려 주는 시간**이다.

## 4. 디스크가 찰 때 · VM 디스크를 늘릴 때 (2026-08-07)

### 4-1. 먼저 «누가 먹었나» 를 가른다 — **우리 작업이 아닐 수 있다**

2026-08-07 에 하루 두 번 잰 결과, 증가분의 대부분이 **프로젝트 밖**이었다:

| 자리 | 정체 |
|---|---|
| `/var/cache/PackageKit` | 🔴 GNOME 이 업데이트를 **미리 내려받아 둔다**. 1.0G 짜리 `linux-firmware` RPM 하나가 오전에 지웠는데 **같은 날 09:07 에 또 받아졌다** |
| `~/.npm/_cacache` | ⚠ **pnpm 만 쓰는데도 되살아난다**(10:06 재생성). 원인 미상 |
| `<repo>/glassvue-backend/build` | 테스트·변형 주입을 돌린 워크트리에서 커진다(리포트·JaCoCo 누적) |

→ **지우기 전에 원인을 재고**, 지운 뒤에도 **다시 차는지** 본다. 반복되면 지우는 건 대책이 아니다.

```
du -shx /* 2>/dev/null | sort -rh | head
find / -xdev -type f -size +200M -newermt "<오늘 00:00>" -printf '%s %TH:%TM %p\n' 2>/dev/null | sort -rn | head
```

⚠ **`du` 와 `df` 가 어긋나면 그 차이를 먼저 설명한다.** 2026-08-07 에 4.7G 가 비었는데,
`/opt/oracle/oradata` 가 `oracle:oinstall drwxr-x---` 라 **읽지 못해 0 으로 센 것**이었다.
그 4.7G 는 **지울 수 없는 DB 데이터**다 — 넘겼으면 «더 지울 게 있다» 로 착각했을 자리다.

### 4-2. 🔴 VM 디스크 확장 — **스냅샷이 있으면 늘어나지 않는다**

> **사고 (2026-08-07)**: `VBoxManage modifymedium disk … --resize 51200` 이 **성공했는데**
> 게스트는 계속 30G 였다. 원인은 **스냅샷**이었다.

```
TestVm.vdi                 51200 MB   base          ← 늘어났다
  └─ {93119613-…}.vdi      30720 MB   differencing  ← VM 이 실제로 쓰는 것
```

⚠ **차등 디스크는 `--resize` 가 안 된다.** 길은 **스냅샷 병합** 하나뿐이다.
⚠ **그래서 「파티션 만지기 전에 스냅샷을 뜬다」 는 여기서 틀린 안전망이다** — 스냅샷 자체가
디스크 구조를 바꾼다. 디스크를 만지는 작업의 안전망은 **VM 폴더 복사(또는 클론·export)** 다.

**게스트에서 원인을 좁히는 법** — 「커널이 낡은 값을 본다」와 구분해야 한다:

```
uptime -s                      # 정말 재부팅했나 (재개면 무의미)
cat /sys/class/block/sda/size  # 커널이 보는 섹터 수
dmesg | grep -i "sda.*logical blocks"   # 부팅 시점에 붙어 있던 크기
lsblk -o NAME,SIZE,SERIAL      # 🔴 SERIAL 이 VDI UUID 다 (VB<uuid앞자리>-…)
```
→ **부팅 로그의 크기가 옛날 값이면 게스트 문제가 아니다** — 그 디스크가 안 붙어 있는 것이다.
→ `SERIAL` 로 호스트 `VBoxManage list hdds` 에서 **어느 파일이 진짜 붙어 있는지** 지목할 수 있다.
   `Parent UUID` 가 `base` 가 아니면 차등 디스크 위에서 돌고 있다는 뜻이다.

### 4-3. 늘린 뒤 — **디스크가 커진 것과 `/` 가 커진 것은 다르다**

`lsblk` 에서 `sda` 만 커지고 `sda2`·`rl-root` 는 그대로다. 셋을 차례로 밀어야 한다:

```
sudo parted /dev/sda resizepart 2 100%   # 마지막 파티션일 때만
sudo reboot                              # 루트가 올라간 디스크라 커널 재읽기가 필요
sudo pvresize /dev/sda2
sudo lvextend -l +100%FREE /dev/mapper/rl-root
sudo xfs_growfs /
```

⚠ **재부팅 후에도 `sda2` 가 그대로면 거기서 멈춘다** — `pvresize`·`lvextend` 는 무의미하다.
⚠ parted 의 *"/etc/fstab 정보를 업데이트해야 합니다"* 는 **정형 안내라 무시한다** —
UUID 도 파티션 번호도 안 바뀌므로 `fstab` 은 그대로 맞다.
⚠ **`xfs` 는 늘릴 수만 있고 줄일 수 없다.** 되돌리려면 4-2 의 폴더 복사본뿐이다.
⚠ `growpart` 가 없어도 **`parted` 로 된다** — 버전 고정 규칙(CLAUDE.md)이 있으니 설치부터 하지 않는다.

---

## 5. 크래시·강제 재부팅이 남긴 것 (2026-08-21)

> ⚠ **§3 과 자리를 나눈 이유**: §3 은 «서비스가 안 뜬다» 이고 여기는 «껐다 켰더니 딴 게 깨졌다» 다.
> 🔴 **한 번의 재부팅이 증상 여럿을 동시에 만든다** — 그때 증상마다 따로 파고들면 «각각 다른 고장»
> 이라는 그림이 그려진다. **먼저 `uptime -s` 를 본다**(2026-08-21 실측: 그 한 줄이 넷을 설명했다).

### 5-1. 🔴 **git 이 잘렸을 때 복구 순서**

> **사고 (2026-08-21)**: 강제 재부팅 뒤 `git status` 가 `fatal: .git/index: index file smaller than
> expected`, `git log` 가 `fatal: 현재 브랜치가 망가진 것처럼 보입니다` 였다.
> 실측: **`.git/index` 0바이트 · `.git/refs/heads/main` 0바이트 · loose object 5개가 0바이트**
> (성한 object 는 1053개였다 — **손상은 «마지막에 쓰인 것» 에 몰린다**).

⚠ **당황해서 re-clone 하면 안 된다** — 커밋 안 된 작업 트리가 함께 날아간다.
🔴 **작업 트리는 대개 멀쩡하다.** 깨진 건 `.git/` 안쪽이다.

**순서**(각 단계가 다음 단계의 근거를 만든다):

1. **되돌아갈 SHA 를 먼저 찾는다** — 이 둘은 크래시에 잘 살아남는다:
   - `tail -3 .git/logs/HEAD` (reflog — 마지막 커밋 SHA 가 그대로 있다)
   - `git ls-remote origin refs/heads/main` (원격 tip)
   🔴 **둘이 다르면 «push 는 됐는데 로컬만 날아간» 것**이다. 2026-08-21 이 그 경우였다 —
   원격이 로컬보다 **한 커밋 앞서** 있었고, 그 커밋의 object 가 로컬에선 0바이트였다.
2. **객체가 성한지 확인한다**: `git cat-file -t <sha>` → `commit` 이 나와야 한다.
3. **0바이트 object 를 치운다**: `find .git/objects -type f -size 0 -delete`
   ⚠ **내용이 없는 파일이라 지워도 잃을 것이 없고**, 지워야 `git fetch` 가 다시 받는다.
   안 지우면 fetch 가 «이미 있다» 로 건너뛰어 **영영 안 낫는다.**
4. **0바이트 loose ref 를 치우고 곧바로 세운다**:
   `rm -f .git/refs/heads/main && git update-ref refs/heads/main <sha>`
   🔴 **`git update-ref` 만으로는 안 된다** — 깨진 ref 는 잠금을 못 잡아
   `cannot lock ref: reference broken` 이 난다. **지우는 것이 먼저다.**
   ⚠ **지운 채로 두면 `packed-refs` 의 옛 SHA 로 조용히 되돌아간다**(이 레포는 08-14 판이 들어 있다) —
   **지우기와 세우기는 한 호흡**이어야 한다.
5. **인덱스를 다시 만든다**: `rm -f .git/index && git reset`
   ⚠ mixed reset 이라 **작업 트리는 안 건드린다.** (HEAD 가 아직 안 고쳐졌으면 빈 인덱스가 만들어져
   **모든 파일이 untracked 로 보이는데**, 놀라지 말 것 — 4번을 끝내고 다시 하면 원복된다.)
6. **원격에서 마저 받는다**: `git fetch origin` → `git fsck` 가 깨끗해질 때까지.
7. **원격이 앞서 있었으면 ff 한다.** ⚠ 그 커밋이 추가하는 파일이 작업 트리에 **untracked 로 남아
   있으면** checkout 이 막힌다 → **먼저 내용을 대조**하고(`sha256sum` 두 개) 같으면 지운 뒤 ff 한다.

⚠ **사후 확인은 `git fsck --no-progress` 가 `dangling` 말고 아무것도 안 뱉는 것**이다.
