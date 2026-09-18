#!/usr/bin/env bash
# 운영 데이터 백업 — **DB `ESP` 스키마(expdp) + 업로드 이미지(tar)** 한 벌 (2026-09-18, BACKLOG F-5).
#
# 왜 스크립트인가: 백업 절차가 어디에도 없었다(F-5). `infra/README` 의 「처음부터 세울 때」는 **스키마**(Flyway)만
# 다루고 **데이터**는 안 다뤘다 — VM 이 날아가면 주문·회원이 전부 사라진다. 그리고 이 VM 은 **재부팅이 잦다**
# (2026-09-17 두 번 · 09-18 한 번).
#
# 🔴 **DB 만 뜨면 이미지를 잃는다** — 업로드 파일은 DB 밖(`/var/www/glassvue-uploads`)에 있고 DB 에는 경로만 있다.
#    그래서 둘을 **같은 타임스탬프**로 한 벌로 뜬다(따로 뜨면 «어느 덤프와 어느 이미지가 짝인가» 를 잃는다).
# ⚠ `.env` 는 여기 없다 — 비밀값이라 사용자가 별도 보관처에 둔다(infra/README). 셋이 모여야 서버가 선다.
# 🔴 **이 폴더는 VM 안이다** — VM 과 함께 사라진다. 끝에 찍는 `scp` 로 **호스트에 가져가야** 백업이 된다.
#
# 실행: scripts/backup-db.sh   (.env 를 읽는다 · sudo 불필요 — 1회 설정은 infra/README 「데이터 백업」)
#
# 종료코드: 0 = 백업·대조 성립, 1 = 실패, **2 = 판정 불가**(.env·폴더·디렉터리 객체 없음).
# ⚠ 판정은 expdp 종료코드만으로 하지 않는다 — «성공적으로 끝났다» 와 «다 떴다» 는 다르다(WA §3-6).
#   **내보낸 테이블 수 = DB 의 테이블 수**, **tar 안의 파일 수 = 폴더의 파일 수** 를 센다.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR=/opt/glassvue-backup          # DB 디렉터리 객체 GLASSVUE_BACKUP 이 가리키는 곳
DIR_OBJECT=GLASSVUE_BACKUP
UPLOAD_DIR=/var/www/glassvue-uploads     # application.yml image.dir
KEEP=7                                   # 몇 벌 남기나 — 넘는 옛 벌은 지운다

if [ ! -f "$REPO_DIR/.env" ]; then
  echo "⚠ .env 가 없다 — 접속 정보를 못 읽는다. **판정 불가**."; exit 2
fi
set -a; . "$REPO_DIR/.env"; set +a
if [ ! -d "$BACKUP_DIR" ] || [ ! -w "$BACKUP_DIR" ]; then
  echo "⚠ $BACKUP_DIR 가 없거나 쓸 수 없다 — 1회 설정(infra/README 「데이터 백업」)이 안 됐다. **판정 불가**."; exit 2
fi

export ORACLE_HOME="${ORACLE_HOME:-/opt/oracle/product/19c/dbhome_1}"
export PATH="$ORACLE_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$ORACLE_HOME/lib:${LD_LIBRARY_PATH:-}"
# ⚠ 영어로 고정한다 — 아래에서 expdp 로그의 `exported`·`successfully completed` 를 **센다.**
#   한국어 NLS 면 문구가 바뀌어 그 셈이 조용히 0 이 된다.
export NLS_LANG=AMERICAN_AMERICA.AL32UTF8

STAMP=$(date +%Y%m%d-%H%M%S)
DUMP="esp-$STAMP.dmp"
LOG="esp-$STAMP.log"
TAR="uploads-$STAMP.tar.gz"
CONNECT="$DB_USER/$DB_PASSWORD@//$DB_HOST:$DB_PORT/$DB_SERVICE"

# 🔴 **비밀번호를 명령줄에 올리지 않는다** — 명령줄 인자는 `/proc/<pid>/cmdline` 에 보인다 — sqlplus 는 뜬 직후 접속 문자열을 공백으로 덮지만(2026-09-18 실측) 그 전 짧은 창이 있고, expdp·java 는 덮어 준다는 보장이 없다
#    (db/migration/README 가 2026-08-04 에 java 의 `--args` 를 버린 것과 같은 판단). 권한 600 파라미터 파일로 넘기고 지운다.
PARFILE=$(umask 077; mktemp)
SQLFILE=$(umask 077; mktemp)
trap 'rm -f "$PARFILE" "$SQLFILE"' EXIT
printf 'userid=%s\nschemas=%s\ndirectory=%s\ndumpfile=%s\nlogfile=%s\n' \
  "$CONNECT" "$DB_USER" "$DIR_OBJECT" "$DUMP" "$LOG" > "$PARFILE"

echo "▶ DB — expdp schemas=$DB_USER → $BACKUP_DIR/$DUMP"
expdp parfile="$PARFILE" > /dev/null 2>&1
EXPDP_RC=$?

# 대조 기준: 지금 DB 의 테이블 수. sqlplus 도 `connect` 를 입력으로 넘겨 명령줄에 비밀번호를 안 남긴다.
printf 'whenever sqlerror exit 2\nconnect %s\nset pagesize 0 feedback off heading off\nselect count(*) from user_tables;\nexit\n' \
  "$CONNECT" > "$SQLFILE"
TABLES=$(sqlplus -s -L /nolog < "$SQLFILE" | tr -d '[:space:]')

if [ ! -r "$BACKUP_DIR/$LOG" ]; then
  echo "  ✗ 로그를 못 읽는다($BACKUP_DIR/$LOG) — expdp 종료코드 $EXPDP_RC. **판정 불가**."; exit 2
fi
EXPORTED=$(grep -c '^\. \. exported ' "$BACKUP_DIR/$LOG")
ROWS=$(grep '^\. \. exported ' "$BACKUP_DIR/$LOG" | awk '{s+=$(NF-1)} END {print s+0}')
DONE_LINE=$(grep -m1 'successfully completed' "$BACKUP_DIR/$LOG")
DUMP_SIZE=$(stat -c %s "$BACKUP_DIR/$DUMP" 2>/dev/null || echo 0)

FAIL=0
echo "  expdp 종료코드 $EXPDP_RC · 덤프 $(numfmt --to=iec "$DUMP_SIZE") · 내보낸 테이블 $EXPORTED / DB 테이블 $TABLES · 행 합계 $ROWS"
if [ "$EXPDP_RC" -ne 0 ] || [ -z "$DONE_LINE" ]; then
  echo "  ✗ expdp 가 성공으로 끝나지 않았다 — $BACKUP_DIR/$LOG 를 본다"; FAIL=1
fi
if ! [[ "$TABLES" =~ ^[0-9]+$ ]] || [ "$TABLES" -eq 0 ]; then
  echo "  ⚠ DB 테이블 수를 못 셌다('$TABLES') — 대조 **판정 불가**."; exit 2
fi
if [ "$EXPORTED" -ne "$TABLES" ]; then
  echo "  ✗ 내보낸 테이블($EXPORTED)이 DB 테이블($TABLES)과 다르다 — **덜 떴다**"; FAIL=1
fi

echo "▶ 이미지 — tar $UPLOAD_DIR → $BACKUP_DIR/$TAR"
FILES_ON_DISK=$(find "$UPLOAD_DIR" -type f | wc -l)
tar -czf "$BACKUP_DIR/$TAR" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
TAR_RC=$?
FILES_IN_TAR=$(tar -tzf "$BACKUP_DIR/$TAR" 2>/dev/null | grep -vc '/$')
echo "  tar 종료코드 $TAR_RC · 폴더 파일 $FILES_ON_DISK / tar 안 파일 $FILES_IN_TAR"
if [ "$TAR_RC" -ne 0 ] || [ "$FILES_IN_TAR" -ne "$FILES_ON_DISK" ]; then
  echo "  ✗ 이미지를 다 못 담았다"; FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then
  echo "✗ 백업 실패 — 이 벌($STAMP)은 믿지 말 것. 옛 벌은 안 지웠다."; exit 1
fi

# 성공했을 때만 옛 벌을 지운다 — 실패한 날 지우면 **남은 좋은 벌까지** 줄어든다.
for pattern in 'esp-*.dmp' 'esp-*.log' 'uploads-*.tar.gz' 'restore-check-*.log'; do
  # shellcheck disable=SC2012
  ls -1t "$BACKUP_DIR"/$pattern 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
    rm -f "$old" && echo "  (옛 벌 지움: $(basename "$old"))"
  done
done

echo "✓ 백업 성립 — $STAMP (최근 ${KEEP}벌 보관)"
echo
echo "🔴 VM 밖으로 가져가야 백업이다 — **호스트(PowerShell)에서**:"
echo "  PS> scp -P 2222 \"ecstel@127.0.0.1:$BACKUP_DIR/*-$STAMP.*\" ."
exit 0
