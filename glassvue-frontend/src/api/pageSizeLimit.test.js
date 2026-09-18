import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { BACKEND_MAIN } from '../test/javaEnum';

/**
 * 화면이 보내는 페이지 크기 ↔ 서버 상한 (2026-09-18, BACKLOG H-3 후속).
 *
 * 서버는 `Pageable` 을 받는 목록에서 `size` 가 상한(`PageSizeGuard.MAX_PAGE_SIZE`)을 넘으면 **400 으로 거절**한다(자르지 않는다).
 * 그러면 누가 DataGrid 에 `[20, 50, 200]` 을 넣는 순간 **그 선택지를 고른 관리자 화면만** 에러가 난다 — 평소엔 아무도 안 고른다.
 * ⚠ 틀리면 «시끄럽게» 넘어지는 쪽이라(WA §2-12) 급하진 않지만, **고를 때까지 모른다**는 점에서 여기서 막는다.
 *
 * 🔴 **상한은 서버 원문에서 읽는다** — 여기 100 을 베껴 적으면 서버가 바뀔 때 같이 낡는다(WA §1-2-1).
 * ⚠ 화면 쪽은 **소스에서 긁는다**(런타임 목록이 없다 — 템플릿 속성·함수 기본값이다). 그래서 «다 셌나» 를 하한으로 못 박는다(WA §3-6).
 * ⚠ 변수로 계산한 크기는 못 본다 — 지금은 전부 숫자 리터럴이다(2026-09-18 실측).
 */

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const GUARD_JAVA = join(BACKEND_MAIN, 'com/glassvue/global/config/PageSizeGuard.java');

function serverMax() {
  const m = /MAX_PAGE_SIZE\s*=\s*(\d+)\s*;/.exec(readFileSync(GUARD_JAVA, 'utf8'));
  return m ? Number(m[1]) : NaN;
}

function sourceFiles(dir = SRC) {
  return readdirSync(dir).flatMap((e) => {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) return sourceFiles(p);
    return /\.(vue|js)$/.test(e) && !/\.test\.js$/.test(e) ? [p] : [];
  });
}

/** 화면이 정하는 크기 전부 — `{ where, kind, value }`. */
function frontendSizes() {
  const found = [];
  for (const file of sourceFiles()) {
    const src = readFileSync(file, 'utf8');
    const where = relative(SRC, file);
    for (const m of src.matchAll(/allowed-page-sizes="\[([^\]]*)\]"/g)) {
      for (const n of m[1].match(/\d+/g) ?? []) found.push({ where, kind: 'allowed-page-sizes', value: Number(n) });
    }
    for (const m of src.matchAll(/:page-size="(\d+)"/g)) found.push({ where, kind: 'page-size', value: Number(m[1]) });
    // ⚠ 앞에 `-` 가 붙은 것(`font-size: 14px` 등 CSS)은 뺀다 — 페이지 크기가 아니다.
    for (const m of src.matchAll(/(?<![-\w])size\s*[=:]\s*(\d+)/g)) found.push({ where, kind: 'size', value: Number(m[1]) });
  }
  return found;
}

describe('화면 페이지 크기 ↔ 서버 상한 (2026-09-18)', () => {
  it('🔴 서버 상한을 원문에서 읽었다 — 못 읽으면 아래 대조가 «NaN 보다 크지 않다» 로 헛돈다', () => {
    expect(serverMax()).toBeGreaterThan(0);
  });

  it('🔴 다 셌다 — 선택지를 가진 표가 6곳 이상, 크기를 정하는 자리가 30곳 이상(0개면 「빈 목록 ⊆ 뭐든」 으로 초록이 된다)', () => {
    const sizes = frontendSizes();
    // 2026-09-18 실측: 선택지 6곳(주문·회원·리뷰·문의·감사·재고 이력) · 전체 53곳(선택지 값 15 · page-size 8 · size 30). 줄면 정규식이 헛돈 것이다.
    // ⚠ 이 단언이 **만들자마자 한 번 값을 했다** — 편집 실수로 size 수집 줄이 주석에 붙어 0개가 됐는데 여기서 빨개졌다.
    expect(new Set(sizes.filter((s) => s.kind === 'allowed-page-sizes').map((s) => s.where)).size).toBeGreaterThanOrEqual(6);
    expect(sizes.length).toBeGreaterThanOrEqual(30);
  });

  it('🔴 화면이 보내는 어떤 크기도 서버 상한을 안 넘는다 — 넘으면 그 선택지를 고르는 순간 400 이다', () => {
    const max = serverMax();
    const over = frontendSizes().filter((s) => s.value > max).map((s) => `${s.where} ${s.kind}=${s.value}`);
    expect(over).toEqual([]);
  });
});
