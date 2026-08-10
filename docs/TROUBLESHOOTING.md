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
| `BUILD SUCCESSFUL` 인데 실제로 아무것도 안 돌았다 | `.env` 미소싱(통합 테스트 skip) · `Task :test UP-TO-DATE`(환경변수는 Gradle 입력이 아니다) | WA §3 — 판정은 `tests`·`skipped` **숫자로만** |
| 통합 테스트가 **무더기로** 컨텍스트 로딩 실패 | 코드가 아니라 **인프라 접속**(DB IP·기동 여부). `Caused by` 최하단까지 내려가면 `ORA-12170` 이 보인다 | WA §5 |
| 테스트가 **간헐적으로** 실패 | 운영 프로세스가 같은 Redis 를 건드린다(조회수 플러셔가 테스트 키까지 SCAN+GETDEL) | WA §3 — 정리가 아니라 **격리**(`spring.data.redis.database`) |
| 로그인 실패를 쓰는 테스트가 어느 날 429 | MockMvc 는 IP 가 전부 `127.0.0.1` → 시도 제한 카운터를 공유 | WA §3 — `X-Real-IP` 로 자기 IP 를 준다 |
| 변형을 넣었는데 **하나도 안 빨개진다** | 테스트가 그 경로에 못 닿았거나, 그 규칙이 코드에 아예 없다 | WA §3 |
| 변형을 넣었더니 **테스트가 아니라 워커가 죽는다** | 운영 코드가 한 겹 방어뿐(가드 하나에만 안전이 걸려 있다) | WA §3 · `handoffs/2026-08-05-handoff.md` §6-3 |
| **카운트 쪽만** 망가뜨린 변형이 안 잡힌다 | 테스트가 `content[*]` 만 보고 **총건수를 안 본다**. 게다가 결과가 한 페이지에 들어가면 count 쿼리가 **아예 안 돈다** | WA §3 — 결과 수 > `size` 로 만들고 `totalElements` 를 단언 · `handoffs/2026-08-06-handoff.md` §4-5 |
| **권한 규칙을 지웠는데** 테스트가 통과한다 | 버그가 아니라 **동치 변형**일 수 있다 — 컨트롤러가 `@LoginUser`(required=true)로 받으면 리졸버가 **같은 401** 을 낸다. 매처가 빠져도 안 열리는 경로가 있다 | WA §2-4-1 — 소유자를 **어디서 받나**로 가른다 · `handoffs/2026-08-07-handoff.md` §5-5 |
| DB 제약을 걸었는데 **도는지 확인이 안 된다** | 앱 방어(엔티티·서비스)가 먼저 잡아 제약이 **한 번도 실행되지 않는다** | WA §2-4-2 — `esptest` 에서 직접 INSERT · **거부와 통과를 함께** 본다 |

**DB · 마이그레이션**

| 증상 | 의심 | 어디 |
|---|---|---|
| 한글이 몇 자 안 들어가고 `ORA-12899` | `VARCHAR2(n)` 이 **바이트**(이 DB 는 `NLS_LENGTH_SEMANTICS=BYTE`) | WA §2-2-1 — 항상 `n CHAR` |
| enum 값을 추가했더니 `ORA-02290` | Hibernate 가 만든 CHECK 제약을 `ddl-auto=update` 가 못 고친다 | `handoffs/2026-07-16-handoff.md` · BACKLOG(B-15 주석) |
| 값을 읽을 때 `ORA-18716` | 감사 컬럼이 plain `TIMESTAMP` 다 — `validate` 는 통과하고 **읽을 때** 터진다 | `handoffs/2026-07-24-handoff.md` (V26 사고) |
| 빈 DB 에서만 마이그레이션 실패 | V1 이 baseline 시점 스키마가 아니다 | WA §2-2 — `esptest` 로 상시 검증 |

**배포 · 운영**

| 증상 | 의심 | 어디 |
|---|---|---|
| 배포했는데 **옛 코드**가 나갔다(스크립트는 정상 종료) | `main` ff 머지를 안 했다 — 스크립트는 **main 워크트리**에서 빌드한다 | WA §5 — `check-deploy-branch.sh` |
| 배포했는데 **화면이 그대로** | 브라우저가 옛 JS 번들을 캐시 | WA §5 — ①API 응답 ②배포된 번들 ③하드 새로고침 순으로 가른다 |
| 미인증 요청이 401 → "엔드포인트 있음"으로 판단했다 | **없는 경로도** Security 필터에서 401 이 난다 | WA §5 — OpenAPI·번들 해시 같은 **확실한 신호**를 쓴다. ⚠ **배포된 jar 안을 직접 보는 게 가장 싸다**: `unzip -l <jar> \| grep <새 클래스\|새 마이그레이션>` (2026-08-10 실측) |
| 로그를 뒤졌는데 **0건**이라 "안 밟혔다"고 판단했다 | **재부팅이 저널을 지웠다.** 이 VM 의 journald 는 휘발성이라 **현재 부팅분만** 남는다 — 날짜가 같아도 갈린다 | WA §3-3-1 — 판정 **전에** `uptime -s`. 대상 시각이 그보다 앞이면 그 0 은 무효다. ⚠ 돈·재고는 **원장(DB)** 으로 본다(`point_history`·`stock_history`) · `handoffs/2026-08-10-handoff.md` §6 |
| 취소·반품했는데 **재고가 일부만** 복원됐다 | 그 주문이 가리키는 **옵션(`product_variant`)이 이미 삭제**됐다. `increaseStock` 은 조용히 넘어가고 **이력도 안 남긴다**(재고가 안 변했는데 남기면 원장이 거짓이 된다) — **설계다** | 옵션 생사를 먼저 본다: `select i.product_name, v.id from order_item i left join product_variant v on v.id=i.variant_id where i.order_id=…` · `handoffs/2026-08-10-handoff.md` §9-2 |
| `systemctl is-active` 는 `active` 인데 안 된다 | 프로세스는 살아 있고 기능은 죽었다 | WA §5 — `/actuator/health` 로 본다 |
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
| 목록에서 **상품명 칸이 비어 있다** | 뜻이 **둘**이라 그것부터 가른다: ①상품이 지워졌다(느슨한 참조라 문의·리뷰는 함께 안 지워진다) ②애초에 상품이 없는 **일반 문의**다. 유형 열을 함께 보면 갈린다 | `InquiryAdminView.productText` · `handoffs/2026-08-07-handoff.md` §5-1 |

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
