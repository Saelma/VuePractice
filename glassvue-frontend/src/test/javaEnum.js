/**
 * 백엔드 Java enum 을 **원문에서** 읽는다 — 화면 라벨 맵과 대조하는 드리프트 테스트들이 같이 쓴다 (2026-09-18, O-8).
 *
 * 🔴 **파서가 두 벌이었고 둘 다 같은 가정에 기대고 있었다** — «값은 4칸 들여쓰기로 줄 맨 앞에 선다».
 *    `order.test.js`·`audit.test.js` 가 각자 정규식을 들고 있었는데, 둘 다 **중첩 enum**
 *    (`PromotionSpanResponse.Kind` — 8칸)을 못 읽는다. 그리고 `audit.test.js` 쪽은 2026-09-11 에
 *    «마지막 값 뒤에 `;` 가 없는» 모양을 한 번 더 고쳤는데 **`order.test.js` 쪽은 그대로였다** —
 *    파서가 둘이면 고친 것도 둘로 갈린다.
 *
 * 🔴 **끝은 «최상위 `;` 또는 짝이 맞는 `}`» 다.** 2026-09-18 에 이 파서를 만들기 전 탐색에서 «첫 `;` 까지» 로
 *    읽었다가 `Kind`(`;` 없이 `}` 로 끝난다)에서 **enum 을 지나쳐 다음 메서드의 `;` 까지** 읽었다 —
 *    마지막 값 `SALE` 에 메서드 본문이 붙어 값으로 안 쳐졌고, `Kind` 를 **3개가 아니라 2개**로 셌다.
 *    틀린 셈이 «2/2 전부 있음» 으로 나와 **성립처럼 보였다**(WA §3-6). 09-11 `AuditTargetType` 과 같은 모양이다.
 * 🔴 **주석을 먼저 지운다** — 주석 안의 `;`·`,`·`}`·대문자 단어가 값·끝으로 읽히지 않게.
 *    ⚠ 문자열도 같이 비운다 — 값 인자에 `"https://…"` 같은 것이 오면 `//` 가 주석으로 읽힌다.
 *    **지금 그런 enum 은 없다**(2026-09-18 실측) — 예방이라 백엔드 원문으로는 안 밟힌다.
 *    그래서 파서 자체를 합성 원문으로 따로 시험한다(`javaEnum.test.js`).
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));

/** 백엔드 main 소스 루트. */
export const BACKEND_MAIN = resolve(HERE, '../../../glassvue-backend/src/main/java');

/** 주석·문자열·문자 리터럴을 한 번에 걷어낸다(문자열 안의 `//`·`/*` 가 주석으로 안 읽히게 한 번의 훑기로). */
function stripCommentsAndLiterals(src) {
  let out = '';
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    const n = src[i + 1];
    if (c === '/' && n === '/') {
      while (i < src.length && src[i] !== '\n') i += 1;
    } else if (c === '/' && n === '*') {
      const end = src.indexOf('*/', i + 2);
      i = end < 0 ? src.length : end + 2;
      out += ' ';
    } else if (src.startsWith('"""', i)) {
      const end = src.indexOf('"""', i + 3);
      i = end < 0 ? src.length : end + 3;
      out += '""';
    } else if (c === '"' || c === "'") {
      let j = i + 1;
      while (j < src.length && src[j] !== c) j += src[j] === '\\' ? 2 : 1;
      i = j + 1;
      out += '""';
    } else {
      out += c;
      i += 1;
    }
  }
  return out;
}

/**
 * `file` 안의 `enum enumName` 의 값 이름들 — 선언 순서대로.
 *
 * 값에 붙는 `(인자)`·`{ 본문 }`·애노테이션은 건너뛴다. 끝은 **최상위의 `;`** 또는 **닫는 `}`** 다
 * (필드가 없는 enum 은 `;` 없이 끝난다 — `AuditTargetType` 이 그 모양이라 2026-09-11 에 한 번 걸렸다).
 * 못 찾으면 빈 배열이다 — ⚠ **부르는 쪽이 «0개가 아니다» 를 단언해야 한다**(WA §3-3, 「0 == 0」은 초록이다).
 */
export function javaEnumValues(file, enumName) {
  return parseJavaEnum(readFileSync(file, 'utf8'), enumName);
}

/** {@link javaEnumValues} 의 본체 — 원문 문자열을 받는다(파서 자체를 합성 원문으로 시험하려고 뗐다). */
export function parseJavaEnum(source, enumName) {
  const src = stripCommentsAndLiterals(source);
  const head = new RegExp(`\\benum\\s+${enumName}\\b[^{]*\\{`).exec(src);
  if (!head) return [];
  const parts = [];
  let cur = '';
  let depth = 0;
  for (let i = head.index + head[0].length; i < src.length; i += 1) {
    const ch = src[i];
    if (ch === '(' || ch === '{') { depth += 1; continue; }
    if (ch === ')' || ch === '}') {
      if (depth === 0) break;
      depth -= 1;
      continue;
    }
    if (depth > 0) continue;
    if (ch === ';') break;
    if (ch === ',') { parts.push(cur); cur = ''; continue; }
    cur += ch;
  }
  parts.push(cur);
  return parts
    .map((p) => p.trim().split(/\s+/).pop())   // `@Deprecated FOO` → `FOO`
    .filter((p) => /^[A-Z][A-Z0-9_]*$/.test(p ?? ''));
}

/** 백엔드 main 의 **모든** enum 선언 — `{ name, file }`. 덮개 테스트가 «다 봤나» 를 여기서 센다. */
export function allJavaEnums(root = BACKEND_MAIN) {
  const found = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir)) {
      const path = join(dir, entry);
      if (statSync(path).isDirectory()) walk(path);
      else if (entry.endsWith('.java')) {
        const src = stripCommentsAndLiterals(readFileSync(path, 'utf8'));
        for (const m of src.matchAll(/\benum\s+([A-Z]\w*)\s*(?:implements[^{]*)?\{/g)) {
          found.push({ name: m[1], file: path, rel: relative(root, path) });
        }
      }
    }
  };
  walk(root);
  return found;
}

/** 이름으로 enum 파일을 찾는다. 같은 이름이 둘이면 던진다 — 조용히 하나를 고르면 엉뚱한 것과 대조한다. */
export function javaEnumFile(enumName, root = BACKEND_MAIN) {
  const hits = allJavaEnums(root).filter((e) => e.name === enumName);
  if (hits.length !== 1) {
    throw new Error(`enum ${enumName}: ${hits.length}곳에서 찾았다 — ${hits.map((h) => h.rel).join(', ')}`);
  }
  return hits[0].file;
}
