import { describe, it, expect } from 'vitest';
import { parseJavaEnum } from './javaEnum';

/**
 * 공용 Java enum 파서 자체 (2026-09-18, O-8).
 *
 * ⚠ **백엔드 원문만으로는 안 밟히는 갈래가 있다** — 문자열 안의 `//`, 값 본문 `{ … }`, 애노테이션은
 *    지금 어느 enum 에도 없다. 드리프트 테스트는 원문을 읽으므로 그 갈래가 틀려도 초록이다.
 *    그래서 여기서는 **합성 원문**으로 그 모양들을 직접 밟는다.
 */
describe('parseJavaEnum', () => {
  it('🔴 주석 안의 `;`·`,`·대문자 단어에 안 속는다 — 주석을 먼저 지운다', () => {
    const src = `
      public enum A {
          /** 첫 값; 여기서 멈추면 안 된다, FAKE 도 값이 아니다. */
          ONE, // 줄 주석; TWO_FAKE,
          /* 블록; 주석 */ TWO,
          THREE;
          private int x;
      }`;
    expect(parseJavaEnum(src, 'A')).toEqual(['ONE', 'TWO', 'THREE']);
  });

  it('🔴 문자열 안의 `//`·`;`·`,` 를 주석·끝으로 읽지 않는다', () => {
    const src = `
      enum Carrier {
          CJ("https://trace.example/a;b,c"),
          ETC("기타 // 직접"),
          POST('x');
          Carrier(String s) {}
      }`;
    expect(parseJavaEnum(src, 'Carrier')).toEqual(['CJ', 'ETC', 'POST']);
  });

  it('값마다 붙은 본문 `{ … }` 과 중첩 괄호 인자를 건너뛴다', () => {
    const src = `
      public enum Transform {
          NONE { Object apply(Object v) { return v; } },
          DATE_START(of(1, (2)), "x") { Object apply(Object v) { return f(v); } };
          abstract Object apply(Object v);
      }`;
    expect(parseJavaEnum(src, 'Transform')).toEqual(['NONE', 'DATE_START']);
  });

  it('`;` 없이 `}` 로 끝나는 enum 의 **마지막 값**을 읽는다 (2026-09-11 에 한 번 놓친 모양)', () => {
    expect(parseJavaEnum('enum T {\n    MEMBER,\n    MARKETING\n}', 'T')).toEqual(['MEMBER', 'MARKETING']);
  });

  it('들여쓰기와 무관하다 — 중첩 enum(8칸)·애노테이션 붙은 값·텍스트 블록', () => {
    const src = `
      public record Span(Kind kind) {
              public enum Kind {
                  @Deprecated ISSUE,
                  USE("""
                      여러 줄; 문자열, SALE_FAKE
                      """),
                  SALE
              }
      }`;
    expect(parseJavaEnum(src, 'Kind')).toEqual(['ISSUE', 'USE', 'SALE']);
  });

  it('이름이 **정확히** 같은 enum 만 읽는다 — 접두사가 같은 다른 enum 과 섞이지 않는다', () => {
    const src = 'enum KindOld { X, Y }\nenum Kind { A, B }';
    expect(parseJavaEnum(src, 'Kind')).toEqual(['A', 'B']);
  });

  it('없으면 빈 배열 — 부르는 쪽이 «0개가 아니다» 를 단언한다(「0 == 0」은 초록이다)', () => {
    expect(parseJavaEnum('class X {}', 'Kind')).toEqual([]);
  });
});
