# 디자인 시스템

이 문서는 **화면을 어떻게 만들지**를 정한다. 설계·아키텍처는 [`ARCHITECTURE.md`](./ARCHITECTURE.md),
작업 방식은 [`docs/WORKING-AGREEMENTS.md`](./docs/WORKING-AGREEMENTS.md).

- **스택**: Vue 3 + Tailwind CSS 4 + DevExtreme 23.1 (`dx.light`)
- **원칙 한 줄**: *데이터를 늘어놓지 않고, **읽는 순서**를 만든다.*

---

## 1. 왜 손대는가 (현재 진단)

지금 화면은 동작하지만 **데이터 나열**에 가깝다. 구체적으로:

| 증상 | 예 |
|---|---|
| 고객 화면인데 운영 툴처럼 보임 | 상품 목록이 `DxDataGrid`(표) — 이미지·가격·별점이 셀 안에 갇힘 |
| 위계가 없음 | 제목·라벨·값이 비슷한 크기·색. 눈이 어디부터 볼지 모름 |
| 토큰 없음 | 색·간격·radius를 화면마다 즉흥적으로(`slate-500`, `rounded`, `p-6` …) |
| 상태가 성의 없음 | 로딩 = "불러오는 중…", 빈 목록 = 회색 한 줄, 에러 = 빨간 박스 |
| 여백이 빡빡 | 카드 안팎 간격이 좁아 정보가 붙어 보임 |

→ 고칠 것은 **①토큰 ②위계 ③여백 ④상태 표현 ⑤고객/운영 화면 분리**다.

---

## 2. 지향점

**절제된 커머스 (잘크·미니멀).** 화려한 장식 대신, 여백과 타이포로 위계를 만든다.

- **강조색은 "거의 검정"** — 채도 있는 브랜드 컬러를 쓰지 않는다. 색을 빼면 남는 건 이미지·타이포·여백이고,
  그게 프로토타입 티를 지우는 가장 확실한 방법이다. 초록/주황/빨강은 **상태 표시에만** 쓴다.
- **거의 무채색 UI + 강조는 행동(CTA)에만.** 링크마다 파란색을 칠하지 않는다.
- **콘텐츠가 주인공.** 상품 이미지·이름·가격이 가장 먼저 읽히게.
- **얇은 경계, 옅은 그림자.** 두꺼운 보더나 진한 그림자는 낡아 보인다.
- **움직임은 거들 뿐.** 150~200ms 정도의 hover/focus 전환까지만.

### ⚠ 제약: 웹폰트는 CDN 금지 — 셀프호스팅만
운영 서버가 **LAN 내부**라 Google Fonts 같은 외부 CDN이 안 뜬다(뜨더라도 느리다).
→ 웹폰트는 **파일을 저장소에 넣어 셀프 호스팅**한다. **Pretendard 적용됨**(2026-07-28):
`src/assets/fonts/PretendardVariable.woff2`(Variable, 전 weight)를 번들이 `/static` 으로 fingerprint 해 emit,
`@font-face`(`src/index.css`)로 등록하고 `--font-sans` 최우선에 둔다. 없으면 시스템 스택으로 폴백.
⚠ **절대 public 경로(`/fonts/..`)로 참조하지 말 것** — rspack CSS 로더가 모듈로 resolve 하려다 빌드가 깨진다.
OFL 라이선스는 `public/fonts/OFL.txt`(공개 서빙)에 동봉.

---

## 3. 디자인 토큰

Tailwind 4의 CSS-first 설정(`@theme`)에 정의한다. **화면에서 임의 색·간격을 쓰지 않고 토큰만 쓴다.**

> 아래 토큰은 `src/index.css`에 실제로 들어가 있다. Tailwind 4는 이 네임스페이스에서
> `bg-brand-600`·`text-ink-900`·`border-line`·`rounded-card`·`shadow-card` 같은 유틸리티를 자동 생성한다.

```css
/* src/index.css */
@theme {
  /* 강조 — 순수 무채색 near-black(파란기 없음, 무신사식 검/흰). CTA·활성·포커스에만.
     ink 그레이와 같은 계열이라 전체 톤이 통일된다(2026-07-28: 기존 gray-800/900 의 남색 틴트 제거). */
  --color-brand-50:  #f5f5f5;
  --color-brand-100: #e5e5e5;
  --color-brand-500: #262626;
  --color-brand-600: #171717;   /* 기본 CTA — 순수 near-black */
  --color-brand-700: #404040;   /* hover — 검정 버튼은 밝아지는 쪽이 자연스럽다(=ink-700) */

  /* 중립 — 대부분의 UI는 여기서 나온다 (순수 회색) */
  --color-ink-900: #0a0a0a;     /* 제목 */
  --color-ink-700: #404040;     /* 본문 */
  --color-ink-500: #737373;     /* 보조 설명 */
  --color-ink-400: #a3a3a3;     /* 비활성·플레이스홀더 */
  --color-line:    #e5e5e5;     /* 경계선 */
  --color-surface: #ffffff;     /* 카드 */
  --color-canvas:  #fafafa;     /* 페이지 배경 */

  /* 의미색 — 상태 표시 전용(장식으로 쓰지 않는다) */
  --color-success: #059669;
  --color-warning: #d97706;
  --color-danger:  #dc2626;

  /* 라운드 — 카드는 넉넉하게, 컨트롤은 절제 */
  --radius-card: 12px;
  --radius-control: 8px;

  /* 그림자 — 아주 옅게. 떠 있는 느낌만. 색도 순수 검정(slate 파란기 제거) */
  --shadow-card: 0 1px 2px rgb(0 0 0 / 0.05);
  --shadow-lift: 0 4px 12px rgb(0 0 0 / 0.08);

  /* 폰트 — Pretendard(셀프호스팅) 우선, 폴백은 시스템 스택(§2 참고) */
  --font-sans: "Pretendard Variable", -apple-system, BlinkMacSystemFont, "Segoe UI", "Pretendard",
               "Apple SD Gothic Neo", "Malgun Gothic", "Noto Sans KR", sans-serif;
}
```

### 타이포 스케일
숫자를 외우지 말고 **역할**로 고른다.

| 역할 | 클래스 | 용도 |
|---|---|---|
| 페이지 제목 | `text-2xl font-bold tracking-tight text-ink-900` | 화면당 1개 |
| 섹션 제목 | `text-lg font-semibold text-ink-900` | 카드/구획 머리 |
| 본문 | `text-sm text-ink-700` | 설명·내용 |
| 보조 | `text-xs text-ink-500` | 라벨·메타(날짜·작성자) |
| 가격/수치 | `text-lg font-semibold text-ink-900` (`tabular-nums`) | 정렬되게 |

> 한글은 같은 크기에서 라틴보다 커 보인다. **본문은 `text-sm`이 기본**이고 `text-base`는 강조용.

### 간격 리듬
- 페이지 여백: `p-6` (모바일 `p-4`)
- 카드 내부: `p-5`
- 섹션 사이: `mt-8`
- 관련 요소 사이: `gap-2`, 그룹 사이: `gap-4`

---

## 4. 레이아웃

```
┌ header (sticky, 높이 56px, 하단 1px line) ────────────┐
├ page  (max-w-6xl mx-auto, p-6) ───────────────────────┤
│   h1 제목 + 우측 액션                                  │
│   ── 필터/툴바 (있으면)                                │
│   ── 콘텐츠 (카드 그리드 또는 그리드/표)                │
└───────────────────────────────────────────────────────┘
```

- **최대폭**: 일반 `max-w-7xl`(1280px — 넓은 모니터의 양옆 휑함을 줄인다, 2026-07-28), 읽는 화면(상세·폼)
  `max-w-3xl`. 헤더·메인·푸터가 모두 같은 폭으로 정렬돼야 한다(App.vue).
- **헤더는 sticky** — 목록을 스크롤해도 이동이 항상 닿는다.

---

## 5. 컴포넌트 패턴

> **공용 클래스로 정의돼 있다** (`src/index.css`). 화면마다 유틸리티를 즉흥 조합하지 말고 이걸 쓴다:
> `page`·`page-narrow`·`page-title`·`section-title`·`muted` / `card`·`card-pad`·`card-link` /
> `btn`+`btn-primary|btn-secondary|btn-ghost|btn-danger` / `badge`+`badge-neutral|success|warning|danger` /
> `field`·`field-label`·`field-error` / `alert-error`·`alert-success` / `skeleton`
> (버튼·배지는 **베이스와 변형을 함께** 쓴다: `class="btn btn-primary"`)

### 버튼
| 종류 | 스타일 | 언제 |
|---|---|---|
| Primary | `bg-brand-600 text-white hover:bg-brand-700` | 화면당 **1개**의 주 행동 |
| Secondary | `border border-line bg-surface text-ink-700 hover:bg-canvas` | 보조 행동 |
| Ghost | `text-ink-500 hover:text-ink-900` | 목록 내 소소한 행동(수정·삭제) |
| Danger | `text-danger hover:bg-red-50` | 파괴적 행동 |

공통: `rounded-control px-3 py-2 text-sm font-medium transition-colors`
포커스: `focus-visible:outline-2 focus-visible:outline-brand-600 focus-visible:outline-offset-2`

### 카드
```html
<article class="rounded-card border border-line bg-surface p-5 shadow-card">
```
클릭 가능한 카드면 `transition hover:shadow-lift hover:-translate-y-0.5`.

### 상태 배지 (주문 상태·문의 상태 등)
`rounded-full px-2 py-0.5 text-xs font-medium` + 의미색의 **연한 배경 + 진한 글자**
(예: 결제완료 `bg-emerald-50 text-emerald-700`). **채도 높은 단색 배경을 쓰지 않는다.**

### 빈 상태 (Empty)
회색 한 줄로 끝내지 않는다. **아이콘/이모지 + 한 줄 설명 + (가능하면) 행동**.
```html
<div class="flex flex-col items-center gap-2 py-12 text-center">
  <span class="text-3xl">🗂️</span>
  <p class="text-sm text-ink-500">아직 상품이 없어요.</p>
  <button …>상품 등록</button>
</div>
```
> 문구는 **상황에 맞게**. 7/20 교훈: 기본 필터 때문에 비어 있는 것과 사용자가 조건을 건 결과는 다른 문구를 쓴다.

### 로딩
"불러오는 중…" 텍스트 대신 **스켈레톤**(`animate-pulse` + 회색 블록)으로 레이아웃을 미리 잡는다.
목록은 카드 3~6개 분량의 스켈레톤.

### 폼
라벨은 `text-xs text-ink-500`, 필드는 세로 `gap-1`, 필드 그룹은 `gap-4`.
에러는 필드 **바로 아래** `text-xs text-danger`. 상단 빨간 박스는 서버/전역 에러에만.

---

## 6. DevExtreme과의 공존 (중요)

DevExtreme은 **자체 테마(dx.light)** 를 들고 온다. Tailwind로 만든 셸과 색·radius가 어긋나면 그게 제일 촌스럽다.

**방침**
1. **운영(관리자) 화면은 DevExtreme 중심** — `DxDataGrid`의 정렬·페이징·필터는 직접 만들 이유가 없다.
2. **고객 화면은 Tailwind 중심** — 상품 목록·상세는 표가 아니라 카드로. DX는 입력 컨트롤 정도만.
3. **접점은 CSS 변수로 맞춘다** — DX의 accent/radius를 우리 토큰에 맞춰 덮어쓴다.

```css
/* dx.light 위에 우리 토큰을 덮어쓴다 */
.dx-widget { font-family: var(--font-sans); }
.dx-button-mode-contained.dx-button-default { background: var(--color-brand-600); }
.dx-texteditor, .dx-button { border-radius: var(--radius-control); }
.dx-datagrid { border-color: var(--color-line); }
```
> DX 내부 클래스를 과하게 파고들지 않는다. **색·radius·폰트 정도만** 맞추고, 나머지는 DX 기본을 존중한다.

---

## 7. 화면별 방침

| 화면 | 방향 |
|---|---|
| 상품 목록 | **DataGrid → 카드 그리드**(이미지·이름·가격·별점). 필터는 상단 바. 이게 "모던"의 체감 1순위 |
| 상품 상세 | 이미지 갤러리(큰 이미지 + 썸네일) ↔ 정보 2단. 구매 액션을 눈에 띄게 |
| 리뷰·문의 | 카드 리스트. 작성 폼은 접었다 펴기 |
| 장바구니·주문내역 | 요약 카드 + 항목 리스트. 합계는 우측 고정 요약 |
| 관리자(주문·카테고리) | **DataGrid 유지**(밀도가 미덕). 셸만 새 토큰 적용 |
| 공지 | 목록은 간결한 리스트, 상세는 읽기 폭(`max-w-3xl`) |

---

## 8. 접근성 (최소선)

- 본문 대비 **4.5:1** 이상 — `ink-500`을 흰 배경 본문에 쓰지 않는다(보조 텍스트 전용).
- **포커스 링을 지우지 않는다**(`outline-none` 금지). `focus-visible`로 키보드일 때만 보이게.
- 아이콘만 있는 버튼엔 `aria-label` (예: ImageUploader의 삭제 버튼).
- 색으로만 상태를 알리지 않는다 — 배지에 **텍스트**를 함께 넣는다.

---

## 9. 적용 로드맵

한 번에 다 갈아엎지 않는다. **토큰 → 셸 → 고객 화면 → 운영 화면** 순.

1. **토큰 + 전역 CSS** (`index.css`) — 색·폰트·radius·그림자
2. **앱 셸** (`App.vue`) — sticky 헤더, 폭 제한, 내비 정리
3. **상품 목록 카드 그리드** — 체감이 가장 큰 화면
4. **상품 상세** — 갤러리 + 구매 액션
5. 리뷰·문의 카드, 장바구니·주문내역
6. 관리자 화면은 셸/토큰만 적용(그리드 구조 유지)
7. 공용 상태 컴포넌트(`EmptyState`, `Skeleton`)를 뽑아 재사용

> 각 단계는 **화면 하나씩** 올려 눈으로 확인하고 넘어간다(WORKING-AGREEMENTS §5 — 프론트는 하드 새로고침으로 확인).
