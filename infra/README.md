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

## 서버를 처음부터 세울 때 빠지는 것

이 디렉토리로도 **자동 복구되지 않는** 것들. 재구축 시 손으로 해야 한다.

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
