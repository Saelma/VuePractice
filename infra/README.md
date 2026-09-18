# infra — 서버 설정 원본

이 디렉토리는 **운영 서버의 설정 파일 사본**이다. 코드가 아니라 **인프라 재현 근거**다.

> **왜 넣었나 (2026-07-22)**: nginx 설정·systemd 유닛이 서버에만 있어서, 저장소를 새로 clone 해도
> **서버를 다시 세울 수 없었다.** 설정에는 사고를 통해 얻은 지식이 들어 있다 —
> `index.html` 만 `no-cache`(캐시된 옛 번들로 오진한 적 있음), `location` 에 `add_header` 가 있으면
> **server 레벨 헤더가 통째로 무시**되는 nginx 규칙 등. 서버가 날아가면 이걸 다시 알아내야 한다.

## 무엇이 어디로 가나

| 저장소 | 서버 | 비고 |
|---|---|---|
| `nginx/nginx.conf` | `/etc/nginx/nginx.conf` | 거의 stock. `include conf.d/*.conf` 때문에 필요 |
| `nginx/conf.d/glassvue.conf` | `/etc/nginx/conf.d/glassvue.conf` | 실질 설정(TLS·프록시·캐시·보안헤더) |
| `systemd/glassvue-backend.service` | `/etc/systemd/system/glassvue-backend.service` | |
| `systemd/oracledb_ESPDB-19c.service.d/override.conf` | `/etc/systemd/system/oracledb_ESPDB-19c.service.d/override.conf` | Oracle 서비스 drop-in — 네트워크 뜬 뒤 시작(아래) |
| `systemd/mailpit.service` | `/etc/systemd/system/mailpit.service` | 로컬 메일 캐처(개발용) — 아래 별도 절 |
| `env.example` | `/home/ecstel/work/.env` | **형식만**. 실제 값은 커밋 금지 |

**여기 없는 것**(의도적):

- **`.env` 실값** — `.gitignore` 로 막혀 있다. 비밀값(`DB_PASSWORD`·`JWT_SECRET`)은 저장소에 두지 않는다.
  **백업처: 사용자가 관리하는 별도 보관처**(2026-07-23 확인). 서버의 `/home/ecstel/work/.env` 가 유일본이 아니다.
  ⚠ `JWT_SECRET` 은 잃으면 복구 불가 — 새로 만들면 기존 토큰이 전부 무효가 되어 전 사용자가 로그아웃된다.
- **TLS 개인키** (`/etc/nginx/ssl/glassvue.key`) — 절대 커밋하지 않는다. 아래 재발급 절차로 만든다.
- **Oracle SysV 스크립트** — `/etc/init.d/oracledb_ESPDB-19c` 는 설치 프로그램이 만든 것이라 손대지 않는다.
  대신 **부팅 순서만 drop-in override** 로 보정한다(위 `override.conf`, 아래 배경 참고).
- **Mailpit 바이너리** (`/opt/mailpit/mailpit`, 약 27MB) — 유닛만 저장소에 있고 **실행 파일은 없다.**
  ⚠ 그래서 재구축 시 **유닛은 살아나는데 가리키는 파일이 없어** 기동에 실패한다. 아래 절의 내려받기가 세트다.

## 반영하는 법 (전부 sudo — CLAUDE.md 상 직접 실행)

바꾸는 순서는 **저장소 먼저, 서버는 그 사본**이다. 서버에서 직접 고치면 이 디렉토리가 낡는다.

```bash
# nginx
sudo cp infra/nginx/conf.d/glassvue.conf /etc/nginx/conf.d/glassvue.conf
sudo /usr/local/sbin/nginx -t && sudo systemctl reload nginx
#     ^^^^^^^^^^^^^^^^^^^^ 소스 빌드라 sudo 의 secure_path 에 없다. 전체 경로로 불러야 한다.

# systemd
sudo cp infra/systemd/glassvue-backend.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl restart glassvue-backend

# Oracle 서비스 drop-in (부팅 순서 보정 — 배경은 아래 6번)
sudo mkdir -p /etc/systemd/system/oracledb_ESPDB-19c.service.d
sudo cp infra/systemd/mailpit.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now mailpit
```

⚠ **Mailpit 은 바이너리를 `/opt/mailpit` 에 두고 나서 유닛을 올린다.**

```bash
# 1) 내려받기(임시 위치는 어디든 상관없다)
mkdir -p ~/tools/mailpit && cd ~/tools/mailpit
curl -L -o mailpit.tar.gz https://github.com/axllent/mailpit/releases/download/v1.30.6/mailpit-linux-amd64.tar.gz
tar xzf mailpit.tar.gz && rm mailpit.tar.gz && ./mailpit version

# 2) /opt 로 옮긴다 — ⚠ 홈에 두면 SELinux 때문에 서비스가 못 뜬다(아래 설명)
sudo mkdir -p /opt/mailpit
sudo cp ~/tools/mailpit/mailpit /opt/mailpit/mailpit
sudo chown root:root /opt/mailpit/mailpit && sudo chmod 755 /opt/mailpit/mailpit
sudo restorecon -v /opt/mailpit/mailpit    # usr_t 라벨 확보
```

> ⚠ **설치 전에 수동 실행분을 먼저 내린다.** 손으로 띄워 둔 Mailpit 이 살아 있으면 systemd 가 올린
> 프로세스가 `listen tcp 127.0.0.1:1025: bind: address already in use` 로 죽는다(`status=1/FAILURE`).
> 2026-07-29 에 실제로 겪었다 — ⚠ **그때 `curl :8025` 는 200 이었다.** 옛 프로세스가 응답한 것이라
> "떠 있으니 됐다"로 오해하기 쉽다(WA §6-1: "지금 떠 있다"는 자동기동 성공의 증거가 아니다).
> 확인: `ss -lntp | grep -E ':1025|:8025'` 로 **누가 쥐고 있는지**를 본다.
>
> ⚠ **왜 홈이 아니라 `/opt` 인가** — SELinux 가 Enforcing 인데 홈 아래 파일은 `user_home_t` 라벨이라
> **systemd(`init_t`)가 실행 자체를 못 한다**(`203/EXEC`, "Permission denied"). 2026-07-29 에
> `~/tools/mailpit` 로 두고 실제로 겪었다 — **셸에서는 되는데 서비스로만 실패**하는, WA §6-2 가
> 기록한 그 신호다. `ls -lZ` 로 컨텍스트를 보면 바로 갈린다.

**Oracle override**

```bash
sudo cp infra/systemd/oracledb_ESPDB-19c.service.d/override.conf \
        /etc/systemd/system/oracledb_ESPDB-19c.service.d/
sudo systemctl daemon-reload
```

## Mailpit (로컬 메일 캐처) — 개발용

비밀번호 재설정·이메일 인증 메일을 **눈으로 확인하기 위한 도구**다(2026-07-29, B-10/B-14).

| 항목 | 값 | 왜 |
|---|---|---|
| SMTP 수신 | `127.0.0.1:1025` | **루프백 고정** — LAN 에서도 안 보인다. nginx 가 프록시하지 않아 외부 경로가 없다 |
| 웹 UI | `127.0.0.1:8025` | 받은 메일을 여기서 본다. 서버 밖에서 보려면 SSH 포트포워딩 |
| 보관 | **메모리**(`-d` 없음) | 재시작하면 사라진다. 검증용이라 보존할 이유가 없고 개인정보가 디스크에 안 남는다 |
| 만료 | `--max-age 7d` | 검증 잔재가 무한정 쌓이지 않게 |
| 실행 계정 | `ecstel` | 고포트라 root 가 필요 없다 |

**⚠ 밖으로 메일을 보내지 않는다.** 받은 걸 붙잡아 보여줄 뿐이라 실수로 실제 발송이 나갈 일이 없다.

### 운영은 메일을 보내지 않는다 — 그 보장은 앱 쪽에 있다

`application.yml` 에 **`spring.mail` 키 자체가 없어서** 스프링이 `JavaMailSender` 빈을 만들지 않고,
`Mailer` 가 no-op 이 된다. dev 프로파일(`application-dev.yml`)만 `127.0.0.1:1025` 를 지정한다.

> ⚠ **"기본값을 비워 두면 꺼진다"는 틀렸다**(2026-07-29 실측). `host: ${MAIL_HOST:}` 로 뒀더니
> 값이 비어도 **프로퍼티는 존재**해서 자동설정이 빈을 만들었고, 빈 host 가 localhost 로 폴백해
> **:1025 에 실제로 붙었다** — 기본 프로파일로 도는 통합 테스트가 캐처로 메일을 보내며 드러났다.
> 그래서 키를 아예 두지 않고, `MailerAutoConfigOffTest` 로 고정했다.
>
> ⚠ 부작용: Mailpit 이 상시 떠 있으면 `:1025` 에 **듣는 쪽이 생긴다.** 누가 `spring.mail` 을 되살리면
> 예전처럼 "연결 거부로 조용히 실패"하는 대신 **메일이 조용히 여기로 들어온다.** 그 방어선이 위 테스트다.

### 확인·운용

```bash
systemctl status mailpit --no-pager | head -5
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8025/     # 200
ss -lntp | grep -E ':1025|:8025'                                    # 누가 쥐고 있는지
```

⚠ **포트를 누가 쥐고 있는지까지 봐야 한다.** 손으로 띄운 인스턴스가 살아 있으면 systemd 쪽이
`address already in use`(`status=1/FAILURE`)로 죽는데, **그때도 `curl :8025` 는 200 이 나온다**
(옛 프로세스가 응답한다). "떠 있으니 됐다"로 오해하기 딱 좋은 자리다.

서버 밖(개발 PC)에서 웹 UI 를 보려면:
```bash
# ⚠ 서버 IP 로 접속하지 않는다 — VM 이 NAT 라 호스트 포트포워딩(127.0.0.1:2222 → VM :22)으로 들어간다.
ssh -L 8025:127.0.0.1:8025 -p 2222 ecstel@127.0.0.1   # 이후 브라우저에서 http://localhost:8025
```

## 호스트에서 접속하는 법 (2026-07-30 — VM 이 NAT 로 바뀐 뒤)

⚠ **서버 IP 로 접속하지 않는다.** VM 이 NAT 라 호스트에서 직접 안 보이고, IP 는 두 번 바뀌었다
(`.36` → `.14` → `10.0.2.15`). 호스트에서는 **포트 포워딩**으로 들어간다.

| 무엇 | 호스트에서 | 비고 |
|---|---|---|
| SSH | `ssh -p 2222 ecstel@127.0.0.1` | 이미 잡혀 있는 규칙 |
| 운영 화면 | `https://localhost:8443/` | 443 포워딩. 인증서 SAN 에 `DNS:localhost` 가 있어 이름 경고 없음 |
| Mailpit 메일함 | SSH 터널: `ssh -L 8025:127.0.0.1:8025 -p 2222 ecstel@127.0.0.1` → `http://localhost:8025` | 루프백 바인딩이라 터널이 필수 |
| **Oracle (DBeaver 등)** | 아래 | |

### DBeaver 로 DB 붙기

**⚠ 두 가지가 기본값과 다르다** — 이걸 몰라서 매번 헤맨다:
- **포트가 1521 이 아니라 `3306`** 이다(이 서버 리스너 설정. 오타가 아니다).
- **SID 가 아니라 Service Name** 으로 붙는다(`espdb`). SID 로 하면 `ORA-12505`.

**방법 A — DBeaver 의 SSH 터널** (VM 설정을 안 건드린다, 권장)

| 탭 | 항목 | 값 |
|---|---|---|
| Main | Connect by | **Service Name** |
| Main | Host / Port | `127.0.0.1` / **`3306`** |
| Main | Service | `espdb` |
| Main | Username / Password | `ESP` / `.env` 의 `DB_PASSWORD` |
| SSH | Use SSH Tunnel | 체크 |
| SSH | Host / Port | `127.0.0.1` / **`2222`** |
| SSH | User | `ecstel` |

**방법 B — VirtualBox 포트 포워딩** (규칙 한 줄, SSH 탭 불필요)

호스트에서 규칙 추가(`oracle,tcp,127.0.0.1,1521,,3306`) 후 DBeaver 는 `127.0.0.1:1521` / Service `espdb`.
VM 재시작은 필요 없다(런타임 적용).

> 검증용 스키마 `esptest` 는 **DB 가 아니라 같은 `espdb` 안의 스키마**다 — 같은 연결에서 Username 만
> `esptest`(비밀번호는 `.env` 의 `ESPTEST_PASSWORD`)로 하나 더 만들면 된다.
>
> ⚠ **회원 계정을 지우려고 DB 에 직접 붙지 않는다.** 2026-07-30 부터 관리자 화면에
> **「회원 삭제」**(SUPER_ADMIN 전용, B-24)가 있고, 그게 연관 데이터 정리·토큰 무효화·감사 이력까지
> 함께 처리한다. DB 에서 행만 지우면 그 셋이 전부 빠진다.

## 드리프트 확인

사본은 **조용히 어긋난다.** 서버에서 급히 고치고 저장소에 반영을 잊으면, 이 디렉토리는
"맞는 것처럼 보이는 틀린 문서"가 된다 — 없느니만 못하다.

```bash
./scripts/check-infra-drift.sh
```

**배포 스크립트가 시작할 때 자동으로 한 번 돌린다** — 배포는 어차피 매번 거치는 관문이라 여기서 알린다.
다르면 경고만 찍고 **배포는 그대로 진행한다.** 막지 않는 게 의도다 — 드리프트가 배포를 막으면
급할 때 스크립트를 우회하게 되어 더 나빠진다.

설정을 만진 직후에는 배포를 기다리지 말고 직접 돌린다.

## TLS 인증서 재발급

self-signed 라 **서버 IP 가 바뀌면 SAN 이 어긋난다**(2026-07-22 에 실제로 발생).
⚠ 그때 *"IP 를 정적으로 고정해 재발은 막았다"* 고 적었는데 **2026-07-30 에 또 바뀌었다**(어댑터가 NAT 대역
→ `10.0.2.15`). 그래도 **재발급하지 않았다** — SAN 에 `IP:127.0.0.1`·`DNS:localhost` 가 함께 있어서
호스트 포트포워딩으로 `https://localhost/` 로 들어가면 이름이 맞는다. **살아남은 건 IP 가 아니라 DNS·루프백 항목이다.**

다시 발급해야 하면 — **IP 항목에 기대지 말고 DNS 로 잡는다**:

```bash
openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout glassvue.key -out glassvue.crt \
  -subj "/C=KR/O=Glassvue/CN=glassvue.local" \
  -addext "subjectAltName=IP:127.0.0.1,DNS:localhost,DNS:glassvue.local"   # 서버 IP 는 넣지 않는다

sudo cp glassvue.crt glassvue.key /etc/nginx/ssl/
sudo /usr/local/sbin/nginx -t && sudo systemctl reload nginx
```

검증은 **`-k` 를 빼고** 한다 — `-k` 로는 SAN 이 맞는지 알 수 없다.

```bash
curl --cacert glassvue.crt https://127.0.0.1/api/products   # 200 이어야 한다 (서버 안에서)
# 호스트에서는: curl --cacert glassvue.crt https://localhost/api/products
```

## 데이터 백업 (2026-09-18, BACKLOG F-5)

위 절들은 **서버를 다시 세우는 법**이고, 여기는 **그 위에 쌓인 데이터**다. 둘은 따로다 —
Flyway 는 스키마를 만들 뿐이고, 주문·회원·이미지는 **이 절차가 없으면 VM 과 함께 사라진다.**

| 무엇 | 어디 | 어떻게 뜨나 |
|---|---|---|
| DB `ESP` 스키마 | Oracle `espdb` | `expdp` — `scripts/backup-db.sh` |
| 업로드 이미지 | `/var/www/glassvue-uploads` | `tar` — 같은 스크립트, **같은 타임스탬프** |
| `.env` | `/home/ecstel/work/.env` | 🔴 **여기 없다** — 비밀값이라 사용자 보관처(위 「여기 없는 것」) |

🔴 **DB 만 뜨면 이미지를 잃는다** — DB 에는 이미지 **경로**만 있다. 🔴 **VM 안의 백업은 백업이 아니다** — 호스트로 가져가야 한다.
⚠ 덤프에는 **회원 이메일·비밀번호 해시**가 들어 있다 — 저장소에 넣지 않고, 호스트 보관처도 `.env` 와 같은 취급을 한다.

### 1회 설정

`DATA_PUMP_DIR` 는 `oracle:oinstall 750` 안이라 **`ecstel` 이 못 읽는다** — 그대로 쓰면 백업마다 sudo 로 꺼내야 하고,
sudo 가 끼는 절차는 건너뛰게 된다(`db/migration/README` 가 같은 이유로 sudo 를 걷어냈다). 그래서 폴더를 따로 둔다:

```bash
# [ecstel@ecstel ~]$  (sudo — 직접 실행)
sudo mkdir -p /opt/glassvue-backup
sudo chown oracle:ecstel /opt/glassvue-backup   # oracle 이 쓰고 ecstel 이 읽고 지운다
sudo chmod 2770 /opt/glassvue-backup            # setgid — 새 파일도 ecstel 그룹으로 생긴다
ls -ld /opt/glassvue-backup                     # drwxrws---. oracle ecstel
```
```sql
-- ESP 로 접속해서(DBA 역할이 있어 sudo 불필요)
create or replace directory GLASSVUE_BACKUP as '/opt/glassvue-backup';
```
⚠ `mkdir && chown && chmod` 를 한 줄로 쳤더니 **`mkdir` 만 되고** `root:root 755` 로 남았다(2026-09-18 — sudo 비밀번호에서 끊긴 것으로 본다).
**`ls -ld` 로 소유자까지 보고** 넘어간다.

### 뜨기 · 가져가기

```bash
# [ecstel@ecstel ~]$
./scripts/backup-db.sh            # sudo 불필요 · 최근 7벌 보관 · 끝에 scp 명령을 찍는다
```
🔴 **백엔드 배포 때 자동으로 돈다**(2026-09-18, `deploy-backend.sh` — 새 jar 가 뜨기 **전**, 즉 Flyway 가 스키마를 바꾸기 직전 벌).
실패해도 배포는 막지 않고 `⚠⚠` 로 알린다. 한 벌 3분 남짓 — 급하면 `SKIP_BACKUP=1 ./scripts/deploy-backend.sh`.
⚠ 자동인 것은 **VM 안에 뜨는 것까지**다 — 호스트로 가져가는 `scp` 는 여전히 손이다.
```powershell
# PS> (호스트) — 스크립트가 찍어 준 줄 그대로
scp -P 2222 "ecstel@127.0.0.1:/opt/glassvue-backup/*-<STAMP>.*" .
```
- 판정은 expdp 종료코드가 아니라 **내보낸 테이블 수 = DB 테이블 수 · tar 안 파일 수 = 폴더 파일 수** 다(종료코드 0 = 성립, 1 = 실패, 2 = 판정 불가).
- 🔴 **비밀번호를 명령줄에 안 올린다** — 권한 600 임시 파라미터 파일로 넘기고 지운다(`/proc/<pid>/cmdline` 은 누구에게나 읽힌다).
- 실패한 날은 **옛 벌을 안 지운다** — 지우면 남은 좋은 벌까지 준다.

### 복구되는지 확인 — `esptest` 에 풀어 본다

```bash
./scripts/check-backup-restore.sh            # 가장 최근 벌 · 또는 STAMP 를 인자로
```
덤프를 **`esptest` 에**(`remap_schema=ESP:ESPTEST`) 풀고, 테이블별 행 수를 **덤프 로그의 `exported … N rows`** 와 대조한다
(운영의 지금 값과 대조하면 백업 뒤에 움직인 만큼 «실패» 로 보인다). 운영 스키마는 건드리지 않는다.
⚠ impdp 가 **종료코드 5** 로 끝나는 게 정상이다 — `ESPTEST` 사용자·`SEQ_ORDER_NO` 가 이미 있어 `ORA-31684` 두 건을 오류로 센다.
그 밖의 `ORA-` 가 나오면 스크립트가 따로 찍는다. ⚠ 돌리고 나면 `esptest` 에 **운영 데이터 사본**이 남는다 — 다음 마이그레이션 검증이 어차피 비운다.

### 실제 복구 (새 서버) — ✅ **`esptest` 로 리허설했다**(2026-09-18) · ⚠ OS 단계는 안 밟았다

**리허설한 것**(`handoffs/2026-09-18-handoff.md` §10): 빈 `esptest` 에 덤프를 풀고(`reset-esptest.sh` → `check-backup-restore.sh`) →
이미지 tar 를 격리 폴더에 풀어 **해시 12/12 일치** → `./scripts/esptest-app.sh` 로 기동(Flyway «적용할 것 없음» · health UP) →
**공개 API 21개 경로가 운영과 한 글자도 같다** · 응답이 가리키는 이미지 전부 존재 · 회원(비밀번호 해시)·주문 해시 합과 **주문번호 시퀀스 다음 값**이 운영과 같다.
**안 밟은 것**: Oracle 설치·`ESP` 사용자 만들기·nginx·유닛(아래 1) — 새 VM 이 있어야 한다. 순서는 이렇게 본다:

1. 위 「서버를 처음부터 세울 때」대로 Oracle·`ESP` 사용자(**빈 스키마**)·nginx·유닛을 세운다. `.env` 를 보관처에서 되돌린다.
2. 1회 설정(폴더 + 디렉터리 객체)을 하고, 호스트의 덤프를 `/opt/glassvue-backup` 에 올린다.
3. `impdp` — `schemas=ESP`, `directory=GLASSVUE_BACKUP`, `dumpfile=esp-<STAMP>.dmp`(비밀번호는 parfile 로).
   🔴 **빈 스키마에 푼다** — 기존 스키마에 `table_exists_action=replace` 로 풀면 **`SEQ_ORDER_NO` 가 옛 값으로 남아**
   다음 주문번호가 복구된 주문과 겹칠 수 있다(위 확인에서 그 시퀀스가 «이미 있음» 으로 건너뛰어졌다).
4. 이미지: `sudo tar -xzf uploads-<STAMP>.tar.gz -C /var/www` → `sudo chown -R ecstel:nginx /var/www/glassvue-uploads` → `sudo chmod 750 /var/www/glassvue-uploads`.
5. 백엔드 기동 — Flyway 이력도 덤프에 있으므로 «적용할 것 없음» 이 나와야 한다.

⚠ **덤프가 옮기는 것**: 데모 계정·약한 비밀번호도 **그대로 따라간다**. BACKLOG §D 의 «외부 노출 단계는 새 환경에 새로 만든다» 는
**데이터를 옮기지 않는다** 는 전제다 — 이 절차는 **같은 성격의 VM 을 되살릴 때** 쓴다.

## 서버를 처음부터 세울 때 빠지는 것

이 디렉토리로도 **자동 복구되지 않는** 것들. 재구축 시 손으로 해야 한다.

⚠ **데이터(주문·회원·이미지)는 이 목록이 아니라 위 「데이터 백업」 절이다.**
⚠ **되살릴 백업이 없으면 → 시드**(2026-09-18, BACKLOG F-3). 🔴 **빈 DB 에는 관리자를 만들 길이 없다** — 가입은 늘 USER 고,
최상위 관리자(SUPER_ADMIN)는 API 로 못 준다. 시드가 **최상위 관리자 1 · 일반 1 · 카테고리 3 · 상품 8** 을 넣는다:

```bash
# [ecstel@ecstel ~]$  — 빈 스키마면 Flyway 가 테이블을 만든 뒤 시드를 넣고 **0 으로 끝난다**(서버로 남지 않는다)
cd /home/ecstel/work/glassvue-backend
set -a; . /home/ecstel/work/.env; set +a
./gradlew bootRun --args="--server.port=8083 --spring.profiles.active=seed"
# 로그의 「[시드]   최상위 관리자 admin / ……」「[시드]   일반 회원 user01 / ……」 두 줄을 **지금 적어 둔다** — 다시 볼 방법이 없다
```
- 🔴 **회원이나 상품이 하나라도 있으면 아무것도 안 하고 1 로 끝난다** — dev 는 운영과 같은 `espdb` 에 붙으므로(`application-dev.yml`)
  «dev 면 시드» 로 두면 운영이 채워진다. 그래서 `seed` 는 **따로 켜야만** 돌고, 켜도 빈 DB 가 아니면 거절한다.
- 비밀번호는 실행 때 무작위(16자)로 만든다 — 저장소에 없다. 들어간 뒤엔 평소처럼 띄운다.
- 검증 계정에서 먼저 보려면 `./scripts/reset-esptest.sh` 로 비운 뒤 **`./scripts/esptest-app.sh seed`** — 🔴 손으로 `SPRING_DATASOURCE_*` 만 바꿔 띄우면
  **Redis·업로드 폴더·배치가 운영과 같다**(공지 조회수 플러셔가 운영 키를 가져간다 · 2026-09-18). 스크립트가 격리한다.

1. **Oracle 19c 설치·`espdb` PDB 생성** — 스키마는 Flyway(`V1__init.sql`)가 만들지만 DB 자체는 아니다.
2. **`/etc/nginx/ssl/`** 인증서 배치(위 절차).
3. **`/var/www/glassvue-uploads/`** 업로드 디렉토리 — 유닛의 `ReadWritePaths` 대상이라 없으면 기동 실패.
4. **IP 정적 고정** — `nmcli con mod enp0s3 ipv4.method manual ...` (2026-07-22 핸드오프 §3-1).
5. **Oracle 부팅 자동시작** — `enable` **하나로는 부족하다**(2026-07-23 확인). 둘 다 필요:
   - `sudo systemctl enable oracledb_ESPDB-19c` — 안 하면 백엔드의 `After=` 가 가리킬 대상이 없어 무의미.
   - **`override.conf` 반영**(위 systemd 절) — **이게 없으면 부팅 때 반드시 실패한다.**
     핵심은 `Environment=SU=/usr/sbin/runuser` 다. 이유는 파일 안 주석에 적어 뒀다.

   > **배경 (2026-07-23)**: `enable` 만 해두고 재부팅했더니 `oracledb_ESPDB-19c` 가 `status=1` 로 실패했고,
   > 백엔드는 DB 를 못 잡아 무한 재시작(`activating`) — **운영이 통째로 내려가 있었다.**
   >
   > 원인은 **SELinux** 였다. 부팅 시 SysV 스크립트는 `init_t` 컨텍스트로 도는데 SELinux 가
   > `init_t → su_exec_t`(`/bin/su`) 실행을 거부한다. 스크립트는 리스너·DB 를 **둘 다 `su` 로** 띄우므로
   > 둘 다 즉시 실패했다(`exit=-13`). `runuser`(라벨 `bin_t`)로 바꾸면 통과한다.
   >
   > **왜 찾기 어려웠나** — 진단 순서를 그대로 남긴다(같은 길을 다시 헤매지 않게):
   > - 스크립트가 두 명령의 출력을 `> /dev/null 2>&1` 로 버려서 **"Failed to start ..." 한 줄만** 남는다.
   >   Oracle 이 아예 실행되지 않았으므로 TNS/ORA 에러도, 리스너 로그도 **어디에도 없다.**
   > - 셸에서 수동 실행하면 `unconfined_t` 라 **항상 성공**한다 → "수동은 되는데 부팅만 실패"로 보인다.
   > - AVC 거부는 **저널이 아니라 `/var/log/audit/audit.log`** 로만 간다. `journalctl | grep avc` 는 빈손이다.
   >   → `sudo ausearch -ts <시각> -te <시각>` 로 봐야 보인다.
   > - 헛짚은 가설 4개(전부 탈락): 호스트명 IPv6 링크로컬 해석 / 부팅 시 네트워크 미준비 /
   >   sqlplus `startup` 실패 / `su` 환경변수 전파 실패.
   >
   > **재부팅 없이 검증하는 법** — `systemctl start` 는 ExecStart 를 **`init_t` 로 실행**하므로 부팅과
   > SELinux 조건이 같다. DB 를 내린 뒤(`sudo bash /etc/init.d/oracledb_ESPDB-19c stop`)
   > `sudo systemctl start oracledb_ESPDB-19c` 가 성공하면 부팅에서도 성공한다.
   > 실측(2026-07-23 09:53): 기동 30초 소요, `Oracle Net Listener started.` ·
   > `Oracle Database instance ESPCDB started.` 두 줄이 찍히고 유닛이 `active`.
   > (실패할 땐 이 두 줄이 없고 1초 만에 끝난다 — 그게 구별점이다.)

   ⚠ **별건 — 스크립트는 PDB 를 못 연다. 대신 saved state 가 연다**: `start()` 의
   `alter pluggable database all open` 에 **세미콜론이 없고** heredoc 종료자 `EOF` 가 들여쓰기돼 있어
   (`<<` 인데 `<<-` 가 아님) `ORA-00933` 이 난다. 즉 **스크립트는 PDB 를 여는 데 매번 실패한다.**

   그런데도 `espdb` 가 열리는 건 PDB **saved state** 때문이다. 확인함(2026-07-23):
   ```
   select name, open_mode from v$pdbs;              → ESPDB  READ WRITE
   select con_name, state from dba_pdb_saved_states; → ESPDB  OPEN
   ```
   같은 날 재부팅에서 스크립트의 `alter ...` 는 실패했는데 `ESPDB` 는 `READ WRITE` 였다 —
   saved state 가 실제로 동작한다는 **실증**이다. saved state 는 CDB 에 영구 저장되므로
   `discard state` 를 명시적으로 하거나 PDB 를 재생성하지 않는 한 유지된다. **추가 조치 불필요.**

   → 다만 **재구축 시에는 이 상태가 없다.** 새로 만든 PDB 는 saved state 가 비어 있어
   부팅 후 `MOUNTED` 로 남는다. 재구축 직후 한 번 걸어 둘 것:
   `alter pluggable database all open; alter pluggable database all save state;`
6. **Mailpit 바이너리 배치** — 유닛은 저장소에 있지만 **실행 파일(27MB)은 없다.**
   `enable` 된 유닛이 없는 파일을 가리켜 기동에 실패하므로, 위 「반영하는 법」의 내려받기 + `/opt` 이동을 함께 한다.
   ⚠ **홈에 두면 SELinux 가 막는다**(`203/EXEC`) — 반드시 `/opt` + `restorecon`.
7. **`.env` 실값 작성** — `env.example` 참고.
