import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import {
  fetchAuditLogs, auditActionText, auditActionBadge,
  AUDIT_ACTION_LABEL, AUDIT_ACTION_BADGE,
} from './audit';
import { clearSession } from '../stores/auth';

/**
 * 관리자 감사 이력 (SUPER_ADMIN 전용).
 *
 * 🔴 **이 파일의 요점은 「드리프트 테스트」다.**
 *
 * 감사는 **남기는 쪽(백엔드 enum)과 보는 쪽(프론트 라벨)이 짝**인데, 한쪽만 자라도 아무것도
 * 터지지 않는다 — 목록은 그려지고 화면도 멀쩡하다. 다만 **빠진 종류를 골라 볼 수 없고**
 * 날문자로 뜬다. 그래서 **사람이 알아채지 못한 채 오래 간다.**
 *
 * > **사고 (2026-08-10 발견)**: `MEMBER_DELETE` 가 2026-07-30(B-24)에 enum 에 들어왔는데
 * > 라벨에 안 들어와 **11일간** 빠져 있었다. 그날 5개가 더 늘어 **9개 중 3개만** 남은 상태였고,
 * > 그때까지 아무 테스트도 빨개지지 않았다.
 *
 * → 그래서 **라벨을 채우는 것으로 끝내지 않고** 여기서 enum 파일을 직접 읽어 대조한다.
 *   `check-infra-drift.sh` 가 서버 설정과 `infra/` 를 대조하는 것과 같은 발상이다.
 */

const HERE = dirname(fileURLToPath(import.meta.url));
const AUDIT_ACTION_JAVA = resolve(
  HERE,
  '../../../glassvue-backend/src/main/java/com/glassvue/domain/audit/entity/AuditAction.java',
);

/**
 * `AuditAction.java` 에서 enum 값을 뽑는다.
 *
 * ⚠ javadoc 줄은 `     * ` 로 시작하므로(공백 5 + `*`) 4칸 들여쓰기 + 대문자 규칙에 안 걸린다.
 *
 * 🔴 **2026-08-20(V53)에 한 번 깨졌다.** 값에 생성자 인자가 붙으면서
 * (`PRODUCT_CREATE(AuditTargetType.PRODUCT),`) 예전 정규식이 **하나도 못 잡았다.**
 * ⚠ 그런데 그건 «대조가 실패» 가 아니라 **«대조가 사라짐»** 이다 — 키 집합끼리 비교하는
 * 아래 두 테스트는 `[] == []` 로 **초록**이 된다. 그걸 막으라고 있던 것이 첫 번째 가드이고,
 * **실제로 그 가드가 잡았다.** 이 주석은 그 가드가 값을 한 일의 기록이다.
 */
function enumValuesFromJava() {
  const src = readFileSync(AUDIT_ACTION_JAVA, 'utf8');
  // 값 뒤의 `(...)` 는 있어도 없어도 된다. 마지막 값은 `;` 로 끝난다.
  return [...src.matchAll(/^ {4}([A-Z][A-Z0-9_]*)\s*(?:\([^)]*\))?\s*[,;]\s*$/gm)].map((m) => m[1]);
}

describe('감사 라벨 ↔ 백엔드 enum 드리프트 (2026-08-10)', () => {
  it('🔴 파서가 값을 실제로 찾았다 — 0개면 아래 대조가 「0 == 0」으로 통과해 버린다', () => {
    // ⚠ 이 단언이 이 파일에서 가장 중요하다. 파일 경로가 바뀌거나 enum 형식이 달라져
    //    파싱이 0개를 내면, 대조 테스트는 **영원히 초록**이면서 아무것도 안 지킨다.
    //    「0 이라는 답에는 이유가 둘 있다 — 밟았는데 0인지, 안 밟아서 0인지」(WA §3-3).
    const values = enumValuesFromJava();
    expect(values.length).toBeGreaterThanOrEqual(4);
    expect(values).toContain('MEMBER_SUSPEND'); // 최초 값 — 이게 없으면 파싱이 틀린 것이다
  });

  it('🔴 라벨 키 집합이 enum 과 **정확히** 같다 (빠지면 필터에서 못 고르고 날문자로 뜬다)', () => {
    // 정렬해서 비교한다 — 순서는 계약이 아니다(선택지 순서는 화면이 정한다).
    expect(Object.keys(AUDIT_ACTION_LABEL).sort()).toEqual(enumValuesFromJava().sort());
  });

  it('🔴 뱃지 키 집합도 enum 과 같다 (빠지면 조용히 회색 — 위험한 조작이 안 띈다)', () => {
    expect(Object.keys(AUDIT_ACTION_BADGE).sort()).toEqual(enumValuesFromJava().sort());
  });

  it('⚠ 되돌릴 수 없는 조작은 danger 다 — 삭제·주문취소는 해제가 없다', () => {
    expect(auditActionBadge('MEMBER_DELETE')).toBe('badge-danger');
    expect(auditActionBadge('ORDER_CANCEL')).toBe('badge-danger');
    // 정지는 해제가 있어 danger 가 아니다(2026-08-10 에 warning 으로 내렸다).
    expect(auditActionBadge('MEMBER_SUSPEND')).toBe('badge-warning');
    expect(auditActionBadge('MEMBER_UNSUSPEND')).toBe('badge-success');
  });

  it('모르는 값은 **날문자 그대로** 돌려주고 뱃지는 중립 — 빈칸으로 삼키지 않는다', () => {
    // enum 이 늘고 배포 순서가 어긋난 순간에도 목록은 읽혀야 한다. 위 대조가 그걸 막는 장치이고,
    // 이건 그래도 새어 나왔을 때의 안전망이다.
    expect(auditActionText('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(auditActionBadge('SOMETHING_NEW')).toBe('badge-neutral');
    expect(auditActionText(null)).toBe('');
  });
});

describe('감사 이력 조회 파라미터', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: null }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  async function callOf(arg) {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;
    await fetchAuditLogs(arg);
    return fetchMock.mock.calls[0][0];
  }

  it('SUPER_ADMIN 전용 경로로 간다', async () => {
    expect((await callOf()).pathname).toBe('/api/admin/audit');
  });

  it('action 이 null 이면(=전체) 파라미터가 아예 빠진다', async () => {
    expect((await callOf({ action: null })).searchParams.has('action')).toBe(false);
    // ⚠ 「전체」에 'ALL' 같은 문자열을 보내면 서버가 enum 변환에 실패해 400 이고,
    //    화면엔 그게 "이력이 없다" 로 보인다(문의·리뷰·주문에서 반복된 자리).
  });

  it('오늘 늘어난 종류로도 좁힐 수 있다 — 이게 안 되면 감사를 남겨도 못 찾는다', async () => {
    expect((await callOf({ action: 'ORDER_CANCEL' })).searchParams.get('action')).toBe('ORDER_CANCEL');
    expect((await callOf({ action: 'INQUIRY_HIDE' })).searchParams.get('action')).toBe('INQUIRY_HIDE');
  });
});
