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
| `env.example` | `/home/ecstel/work/.env` | **형식만**. 실제 값은 커밋 금지 |

**여기 없는 것**(의도적):

- **`.env` 실값** — `.gitignore` 로 막혀 있다. 비밀값(`DB_PASSWORD`·`JWT_SECRET`)은 저장소에 두지 않는다.
- **TLS 개인키** (`/etc/nginx/ssl/glassvue.key`) — 절대 커밋하지 않는다. 아래 재발급 절차로 만든다.
- **Oracle SysV 스크립트** — `/etc/init.d/oracledb_ESPDB-19c` 는 설치 프로그램이 만든 것이라 손대지 않는다.
  대신 **부팅 순서만 drop-in override** 로 보정한다(위 `override.conf`, 아래 배경 참고).

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
sudo cp infra/systemd/oracledb_ESPDB-19c.service.d/override.conf \
        /etc/systemd/system/oracledb_ESPDB-19c.service.d/
sudo systemctl daemon-reload
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
IP 를 정적으로 고정해 재발은 막았지만, 다시 바꿔야 하면:

```bash
openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout glassvue.key -out glassvue.crt \
  -subj "/C=KR/O=Glassvue/CN=glassvue.local" \
  -addext "subjectAltName=IP:192.168.50.14,IP:127.0.0.1,DNS:localhost,DNS:glassvue.local"

sudo cp glassvue.crt glassvue.key /etc/nginx/ssl/
sudo /usr/local/sbin/nginx -t && sudo systemctl reload nginx
```

검증은 **`-k` 를 빼고** 한다 — `-k` 로는 SAN 이 맞는지 알 수 없다.

```bash
curl --cacert glassvue.crt https://192.168.50.14/api/products   # 200 이어야 한다
```

## 서버를 처음부터 세울 때 빠지는 것

이 디렉토리로도 **자동 복구되지 않는** 것들. 재구축 시 손으로 해야 한다.

1. **Oracle 19c 설치·`espdb` PDB 생성** — 스키마는 Flyway(`V1__init.sql`)가 만들지만 DB 자체는 아니다.
2. **`/etc/nginx/ssl/`** 인증서 배치(위 절차).
3. **`/var/www/glassvue-uploads/`** 업로드 디렉토리 — 유닛의 `ReadWritePaths` 대상이라 없으면 기동 실패.
4. **IP 정적 고정** — `nmcli con mod enp0s3 ipv4.method manual ...` (2026-07-22 핸드오프 §3-1).
5. **Oracle 부팅 자동시작** — `enable` **하나로는 부족하다**(2026-07-23 재부팅으로 확인). 세 가지가 다 필요:
   - `sudo systemctl enable oracledb_ESPDB-19c` — 안 하면 백엔드의 `After=` 가 가리킬 대상이 없어 무의미.
   - **`/etc/hosts` 에 `192.168.50.14   ecstel` 핀** — `listener.ora` 가 `(HOST = ecstel)` 로 바인딩하는데,
     핀이 없으면 호스트명이 IPv6 링크로컬(`fe80::…`)로만 잡혀 부팅 초반 리스너 바인드가 실패한다.
   - **`override.conf` 반영**(위 systemd 절) — SysV 유닛엔 네트워크 의존성이 없어 부팅 13초 만에 실행돼
     네트워크가 덜 올라온 채 리스너가 죽는다. `After/Wants=network-online.target` 으로 대기시킨다.
     (`NetworkManager-wait-online` 이 `enabled` 여야 network-online 이 실제로 대기를 건다.)

   > **배경 (2026-07-23)**: `enable` 만 해두고 재부팅했더니 `oracledb_ESPDB-19c` 가 `status=1` 로 실패했다
   > (start→fail 이 같은 1초 — 리스너 즉시 바인드 실패). DB 는 수동 `lsnrctl start` + `startup` 으로 복구했다.
   > 원인은 위 둘(호스트명 미해석 + 네트워크 의존성 부재). 검증 = 재부팅 후 손 안 대고
   > `systemctl is-active oracledb_ESPDB-19c glassvue-backend` 둘 다 `active`.
6. **`.env` 실값 작성** — `env.example` 참고.
