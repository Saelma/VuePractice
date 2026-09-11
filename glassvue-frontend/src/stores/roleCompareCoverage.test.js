import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, relative, sep } from 'node:path';

// 역할 판별의 **덮개** (BACKLOG O-5 · WA §2-12, 2026-09-11).
//
// 🔴 `role === 'ADMIN'` 은 **빠뜨리면 조용한 쪽으로 넘어진다** — SUPER_ADMIN 이 늘 떨어져 나가는데
//    아무것도 안 터진다. `ProductListView` 가 그래서 **최상위 관리자에게 관리 버튼을 안 보여 줬다.**
//    `isAdminRole` 이 바로 그걸 막으려고 있었는데(auth.js 주석) 안 쓰였다 — 주석은 안 지켜진다.
//
// → 역할 문자열과의 비교는 **`stores/auth.js` 한 곳에서만** 한다. 밖에서 비교하면 여기서 빨개진다.
//    정말 필요하면 `ALLOWED` 에 **이유와 함께** 적는다(결정을 강제하는 목록).
// ⚠ 값으로 **쓰는** 것(`? 'USER' : 'ADMIN'` — 서버에 보낼 역할)은 비교가 아니라 안 센다.

const HERE = dirname(fileURLToPath(import.meta.url));
const SRC = resolve(HERE, '..');
const HOME = 'stores/auth.js';

/** 역할 리터럴과의 비교(`===`·`!==`·`==`·`!=`, 좌우 어느 쪽이든). */
const ROLE_COMPARE = /[!=]==?\s*['"](?:SUPER_ADMIN|ADMIN|USER)['"]|['"](?:SUPER_ADMIN|ADMIN|USER)['"]\s*[!=]==?/;

/** 이유와 함께 허용한 자리 — 지금은 없다. `{ file: 'views/X.vue', code: '<그 줄 trim>', why: '…' }` */
const ALLOWED = [];

function sourceFiles(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = resolve(dir, e.name);
    if (e.isDirectory()) return sourceFiles(p);
    if (!/\.(vue|js)$/.test(e.name) || /\.test\.js$/.test(e.name)) return [];
    return [relative(SRC, p).split(sep).join('/')];
  });
}

function hits(file) {
  return readFileSync(resolve(SRC, file), 'utf8').split('\n')
    .map((code, i) => ({ file, line: i + 1, code: code.trim() }))
    .filter((h) => ROLE_COMPARE.test(h.code) && !h.code.startsWith('//'));
}

const FILES = sourceFiles(SRC);

describe('역할 판별은 stores/auth.js 한 곳에서만', () => {
  it('🔴 auth.js 밖에서 역할 문자열과 비교하지 않는다 — isAdminRole / isSuperAdminRole 을 쓴다', () => {
    const offenders = FILES.filter((f) => f !== HOME).flatMap(hits)
      .filter((h) => !ALLOWED.some((a) => a.file === h.file && a.code === h.code))
      .map((h) => `${h.file}:${h.line}  ${h.code}`);
    expect(offenders, 'SUPER_ADMIN 을 놓치는 비교 — stores/auth.js 의 판별 함수를 쓸 것').toEqual([]);
  });

  it('ALLOWED 의 자리는 아직 실제로 있어야 한다 — 낡은 허용은 지운다', () => {
    const stale = ALLOWED.filter((a) => !hits(a.file).some((h) => h.code === a.code));
    expect(stale).toEqual([]);
  });

  // WA §3-6 — «0건» 이 «다 봤다» 인지 «못 봤다» 인지 가른다.
  it('다 셌는가 — 스캐너가 실제 비교를 잡고, 역할을 쓰는 화면을 읽었다', () => {
    // 양성 대조: 판별 함수 자신은 비교를 한다. 여기서 0 이면 정규식이 헛돈다.
    expect(hits(HOME).length).toBeGreaterThanOrEqual(2);
    // 고친 화면들이 스캔 범위 안에 있다(경로·확장자 필터가 빼먹지 않았다).
    expect(FILES).toEqual(expect.arrayContaining([
      'views/ProductListView.vue', 'views/MemberAdminView.vue', 'views/MemberDetailAdminView.vue', HOME,
    ]));
  });
});
