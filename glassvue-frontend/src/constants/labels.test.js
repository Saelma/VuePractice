import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, relative, sep } from 'node:path';
import { PAGER_INFO, PAGER_INFO_MEMBERS } from './labels';

// 페이저 문구의 **덮개** (2026-09-11 · WA §2-12).
//
// 🔴 `"{2}건 중 {0}-{1}"` 한 줄이 **8곳에 복사**돼 있었고, 뜻을 거꾸로 읽은 채였다 — DevExtreme 의
//    `{0}` 은 현재 쪽, `{1}` 은 쪽 수라 91건이면 «91건 중 1-5»(= 5쪽 중 1쪽)가 «1~5번째 행» 으로 읽혔다.
// → 문구는 `constants/labels.js` 한 곳에서만 온다. 화면에 **문자열로 직접** 적으면 여기서 빨개진다.

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function vueFiles(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = resolve(dir, e.name);
    if (e.isDirectory()) return vueFiles(p);
    return e.name.endsWith('.vue') ? [relative(SRC, p).split(sep).join('/')] : [];
  });
}

describe('관리자 표 페이저 문구', () => {
  it('🔴 자리표시자 뜻대로 쓴다 — {2}=총 건수 · {0}=현재 쪽 · {1}=쪽 수', () => {
    expect(PAGER_INFO).toBe('총 {2}건 · {0}/{1}쪽');
    expect(PAGER_INFO_MEMBERS).toBe('총 {2}명 · {0}/{1}쪽');
  });

  it('🔴 화면은 페이저 문구를 **문자열로 직접** 적지 않는다 — 상수를 쓴다(복사본이 다시 갈리지 않게)', () => {
    const files = vueFiles(SRC);
    const literal = files.filter((f) => /\sinfo-text="/.test(readFileSync(resolve(SRC, f), 'utf8')));
    expect(literal).toEqual([]);
    // 다 셌는가 — 상수를 쓰는 자리가 실제로 잡혀야 위 0 이 «다 봤다» 가 된다(WA §3-6).
    const bound = files.filter((f) => /:info-text="PAGER_INFO/.test(readFileSync(resolve(SRC, f), 'utf8')));
    expect(bound.length).toBeGreaterThanOrEqual(7);
  });
});
