<script setup>
/**
 * 프로모션 달력 (2026-08-13, BACKLOG B-27) — **관리자 전용**.
 *
 * 🔴 **목록으로는 안 보이고 달력으로만 보이는 질문 하나**에 답한다: *"이 날 무엇이 **동시에**
 * 돌고 있나"*. 할인이 겹치면 마진이 겹쳐서 깎인다.
 *
 * ⚠ **어제까지는 이 화면이 일러서 안 만들었다.** 쿠폰 5개가 전부 상시라 달력에 그려도 가로줄
 * 다섯 개일 뿐 목록보다 나은 게 없었다 — **겹침이 정보가 되려면 기간이 갈려야** 하고,
 * 이벤트 쿠폰(G-8)이 그 갈림을 만들었다. 그게 이 항목의 착수 조건이었다.
 *
 * 🔴 **2026-08-19(G-5)에 상품 타임세일이 얹혔고, 그때부터 이 화면이 원래 답하려던 질문에 답한다.**
 *    그전까지 겹칠 것이 쿠폰뿐이라 «겹침» 이 사실상 «발급 창끼리» 하나였다. 이제 셋이 겹친다 —
 *    ⚠ **쿠폰 발급 창과 상품 세일이 같은 날에 있으면 그 날은 할인이 두 번 먹는다**
 *    (이미 깎인 세일가 위에 쿠폰이 또 붙는다). 그게 이 격자에서 눈으로 잡히는 것이다.
 *
 * ⚠ **DevExtreme `Scheduler` 를 쓰지 않는다.** 그건 *사람이 시간을 예약하는* 도메인(상담·예약제)의
 * 컴포넌트다. 여기 필요한 건 **기간 막대를 월 격자에 얹는 것**이라 격자를 직접 그린다.
 *
 * ⚠ **고객 배너와 데이터는 같지만 화면·DTO 가 다르다** — 앞으로 여기엔 마케팅 대상 수·할인 원가가
 * 얹히고 그게 고객에게 새면 안 된다.
 *
 * ⚠ **관리자 메뉴에 넣지 않았다** — 메뉴는 「지금 처리할 것」을 여는 자리인데 달력은 답이 아니라
 * **조회 도구**다(AdminMenu 주석의 "길게 만들지 말라"에 걸린다). 쿠폰 화면에서 링크로 들어온다.
 */
import { ref, computed, onMounted } from 'vue';
import { fetchPromotionCalendar } from '../api/coupon';
import EmptyState from '../components/EmptyState.vue';

const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];

/** 격자에서 위로 올릴 순서 — 「겹치면 안 되는 것」이 위다. */
const KIND_ORDER = { ISSUE: 0, SALE: 1, USE: 2 };

/** 막대 종류의 사람 말. 서버 enum 에 값이 늘면 원문을 그대로 되돌린다. */
const KIND_LABEL = { ISSUE: '발급 창', SALE: '타임세일', USE: '사용 기간' };

const data = ref(null);
const loading = ref(true);
const loadError = ref('');
/** 'YYYY-MM'. 빈 값이면 서버가 이번 달(KST)로 답한다 — 화면이 「오늘」을 정하지 않는다. */
const month = ref('');

onMounted(load);

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    data.value = await fetchPromotionCalendar(month.value || undefined);
    month.value = data.value.month; // 서버가 정한 달을 화면 상태로 받아 온다
  } catch (e) {
    // ⚠ 실패를 빈 달력으로 위장하지 않는다 — 빈 격자는 "이 달엔 프로모션이 없다"로 읽힌다.
    data.value = null;
    loadError.value = e.message;
  } finally {
    loading.value = false;
  }
}

function shiftMonth(delta) {
  const [y, m] = month.value.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  month.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  load();
}

const monthLabel = computed(() => {
  if (!month.value) return '';
  const [y, m] = month.value.split('-');
  return `${y}년 ${Number(m)}월`;
});

/**
 * 주 단위로 자른 격자. 각 주는 7칸이고 그 달 밖의 칸은 `null` 이다.
 *
 * ⚠ 요일 계산은 화면이 해도 안전하다(순수 달력 산수라 시간대에 안 흔들린다).
 * **흔들리는 것은 막대의 날짜**라 그쪽만 서버가 잘라 준다.
 */
/**
 * 🔴 **상시 쿠폰은 격자 밖에 둔다** (2026-08-13, 사용자 지적).
 *
 * 상시 쿠폰의 사용 기간은 대개 한 달을 꽉 채워서 **격자를 가로줄로 덮는다.** 위치가 없는 값이라
 * 격자에 있어 봐야 정보가 아니고, 정작 봐야 할 **이벤트 겹침만 묻힌다** — 백로그가 이 화면을
 * 「지금은 이르다」로 미뤄 뒀던 이유(*"가로줄 다섯 개일 뿐"*)가 그대로 재현되는 자리다.
 *
 * ⚠ 다만 **종료일은 버리지 않는다** — 상시 쿠폰에서 유일하게 위치가 있는 값이라, 격자에서 빼면서
 * 그것까지 없애면 «언제 끝나나» 를 어디서도 못 본다.
 */
const alwaysOn = computed(() => {
  if (!data.value) return [];
  return data.value.spans
    .filter((s) => !s.gridded) // 격자에 안 그리는 것 = 상시 쿠폰 (서버가 판단해서 준다)
    .map((s) => ({
      ...s,
      // 이 달 안에서 끝나면 그 날을 적는다. 다음 달로 이어지면 «계속»이라 적을 날이 없다.
      endsThisMonth: !s.continuesAfter,
    }));
});

const weeks = computed(() => {
  if (!data.value) return [];
  const { daysInMonth, firstDayOfWeek } = data.value; // 1=월 … 7=일
  const out = [];
  let cursor = 1 - (firstDayOfWeek - 1); // 첫 주의 «1일 이전» 빈칸까지 포함해 시작
  while (cursor <= daysInMonth) {
    const days = [];
    for (let i = 0; i < 7; i += 1) {
      const day = cursor + i;
      days.push(day >= 1 && day <= daysInMonth ? day : null);
    }
    out.push({ startDay: cursor, endDay: cursor + 6, days, bars: barsIn(cursor, cursor + 6) });
    cursor += 7;
  }
  return out;
});

/**
 * 막대 색 — **채움 강도**로 셋을 가른다(DESIGN 무채색 원칙).
 *
 * 🔴 진하게 채운 것일수록 «겹치면 안 되는 것»이다: 발급 창(검정) → 세일(회색) → 사용 기간(테두리만).
 *    강조색(빨강)을 쓰면 **세일이 발급 창보다 튀어** 읽는 순서가 뒤집힌다.
 */
function barClass(kind) {
  if (kind === 'ISSUE') return 'bg-ink-900 font-medium text-white';
  if (kind === 'SALE') return 'bg-ink-500 font-medium text-white';
  return 'border border-line bg-canvas text-ink-700';
}

/** 그 주에 걸치는 막대만, 주 안의 칸 번호(1~7)로 잘라서 돌려준다. */
function barsIn(weekStart, weekEnd) {
  return data.value.spans
    // 상시 쿠폰은 위 스트립이 맡는다 — 격자에는 «날짜가 뜻을 가지는 것» 만 남긴다.
    .filter((s) => s.gridded)
    .filter((s) => s.endDay >= weekStart && s.startDay <= weekEnd)
    .map((s) => {
      const from = Math.max(s.startDay, weekStart);
      const to = Math.min(s.endDay, weekEnd);
      return {
        ...s,
        col: from - weekStart + 1,
        span: to - from + 1,
        // 잘린 쪽은 모서리를 열어 그린다 — 여기서 시작·끝난 것처럼 보이면 거짓말이다.
        openLeft: s.startDay < weekStart || (s.continuesBefore && from === s.startDay),
        openRight: s.endDay > weekEnd || (s.continuesAfter && to === s.endDay),
      };
    })
    // ⚠ 순서가 뜻을 만든다 — **겹치면 안 되는 것을 위로** 올린다:
    //    발급 창(겹치면 사고) → 세일(상품끼리는 겹쳐도 되지만 쿠폰과 겹치면 이중 할인) → 사용 기간(정상).
    //    같은 종류면 시작이 이른 것부터. 안 그러면 새로고침마다 줄 순서가 바뀐다.
    .sort((a, b) => (a.kind === b.kind ? a.col - b.col : KIND_ORDER[a.kind] - KIND_ORDER[b.kind]));
}
</script>

<template>
  <section class="page">
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="page-title">프로모션 달력</h1>
        <p class="muted">이 달에 살아 있는 <b>쿠폰과 타임세일</b>의 기간을 겹쳐 본다.</p>
      </div>
      <RouterLink to="/admin/coupons" class="btn btn-secondary btn-sm">쿠폰 관리로</RouterLink>
    </div>

    <div class="card p-5">
      <div class="mb-4 flex items-center justify-between gap-3">
        <button type="button" class="btn btn-secondary btn-sm" @click="shiftMonth(-1)">← 이전 달</button>
        <h2 class="section-title">{{ monthLabel }}</h2>
        <button type="button" class="btn btn-secondary btn-sm" @click="shiftMonth(1)">다음 달 →</button>
      </div>

      <!--
        범례가 이 화면의 절반이다 — 두 막대의 뜻이 다르다는 걸 모르면
        「정상인 사용 기간 겹침」을 사고로 읽는다.
      -->
      <!--
        범례가 이 화면의 절반이다 — 세 막대의 뜻이 다르다는 걸 모르면
        「정상인 사용 기간 겹침」을 사고로 읽는다.
        ⚠ **색을 쓰지 않고 「채움 강도」로 가른다**(DESIGN 무채색 원칙). 세일에 빨강을 쓰면
           **겹치면 안 되는 발급 창보다 세일이 더 튀어** 읽는 순서가 뒤집힌다.
      -->
      <div class="mb-4 flex flex-wrap items-center gap-4 text-xs text-ink-500">
        <span class="flex items-center gap-1.5">
          <span class="inline-block h-2.5 w-6 rounded-sm bg-ink-900"></span>
          발급 창 — 「받기」가 열린 구간. <b class="text-ink-700">겹치면 안 된다</b>(등록 때 서버가 막는다)
        </span>
        <span class="flex items-center gap-1.5">
          <span class="inline-block h-2.5 w-6 rounded-sm bg-ink-500"></span>
          타임세일 — 그 상품이 싸게 팔리는 구간
        </span>
        <span class="flex items-center gap-1.5">
          <span class="inline-block h-2.5 w-6 rounded-sm border border-line bg-canvas"></span>
          사용 기간 — <b class="text-ink-700">겹치는 게 정상</b>이다
        </span>
        <span class="basis-full">
          🔴 <b class="text-ink-700">발급 창과 타임세일이 같은 날에 있으면</b> 그 날은
          이미 깎인 세일가 위에 쿠폰이 <b class="text-ink-700">또</b> 붙는다.
        </span>
      </div>

      <p v-if="loadError" class="alert-error">{{ loadError }}</p>

      <!--
        상시 쿠폰 — 격자 위에 따로 둔다. 한 달을 꽉 채우는 막대라 격자 안에 있으면
        가로줄이 되어 이벤트 겹침을 덮는다(위 alwaysOn 주석).
        ⚠ 종료일은 함께 적는다 — 상시 쿠폰에서 유일하게 날짜가 뜻을 가지는 값이다.
      -->
      <div v-if="!loading && alwaysOn.length" class="mb-3 rounded-card border border-line bg-canvas px-4 py-3">
        <p class="text-xs text-ink-500">이 달 내내 도는 상시 쿠폰</p>
        <ul class="mt-1 flex flex-wrap gap-x-4 gap-y-1">
          <li v-for="c in alwaysOn" :key="c.couponId" class="text-sm text-ink-700">
            {{ c.name }}
            <span class="text-xs text-ink-500">
              · {{ c.label }}
              <template v-if="c.endsThisMonth">· <b class="text-ink-700">{{ c.endDay }}일 종료</b></template>
            </span>
          </li>
        </ul>
      </div>

      <div v-if="loading" class="space-y-2">
        <div v-for="n in 5" :key="n" class="skeleton h-16 w-full rounded-card"></div>
      </div>

      <template v-else-if="data">
        <!-- 요일 머리 -->
        <div class="grid grid-cols-7 border-b border-line pb-1">
          <div v-for="d in DAY_LABELS" :key="d" class="text-center text-xs text-ink-500">{{ d }}</div>
        </div>

        <div v-for="(week, wi) in weeks" :key="wi" class="border-b border-line py-1">
          <!-- 날짜 줄 -->
          <div class="grid grid-cols-7">
            <div
              v-for="(day, di) in week.days"
              :key="di"
              class="px-1 py-0.5 text-right text-xs tabular-nums"
              :class="day ? 'text-ink-700' : 'text-transparent'"
            >{{ day ?? '·' }}</div>
          </div>

          <!-- 막대 — 한 줄에 하나씩 쌓아 같은 날에 무엇이 겹치는지 세로로 읽게 한다 -->
          <div v-if="week.bars.length" class="mt-0.5 grid grid-cols-7 gap-y-0.5">
            <div
              v-for="(bar, bi) in week.bars"
              :key="bi"
              class="min-w-0 px-0.5"
              :style="{ gridColumn: `${bar.col} / span ${bar.span}` }"
            >
              <div
                class="truncate rounded-sm px-1.5 py-0.5 text-[11px] leading-tight"
                :class="[
                  barClass(bar.kind),
                  bar.openLeft ? 'rounded-l-none' : '',
                  bar.openRight ? 'rounded-r-none' : '',
                ]"
                :title="`${bar.name} · ${bar.label} · ${KIND_LABEL[bar.kind] ?? bar.kind}`"
              >
                <!--
                  이름과 할인율을 함께 적는다(2026-08-19, 사용자 결정) — 겹침을 볼 때
                  「무엇이 얼마나」가 같이 읽혀야 한다. 칸이 좁으면 말줄임된다(truncate).
                -->
                <span v-if="bar.openLeft">‹ </span>{{ bar.name }}
                <span class="opacity-70">{{ bar.label }}</span><span v-if="bar.openRight"> ›</span>
              </div>
            </div>
          </div>
          <!-- 막대가 없는 주는 자리만 남긴다(격자가 무너지지 않게) -->
          <div v-else class="h-4"></div>
        </div>

        <!--
          ⚠ 빈 달이 정상인 화면이다 — 이벤트가 매달 있지 않다.
          ⚠ 판정은 **격자에 그릴 것**(이벤트) 기준이다. 상시 쿠폰만 있는 달은 격자가 비는 게 맞고,
             그때 「쿠폰이 없다」로 말하면 위 스트립과 어긋난다.
        -->
        <EmptyState
          v-if="!data.spans.some((s) => s.gridded)"
          icon="🗓️"
          message="이 달에는 예정된 이벤트도 타임세일도 없어요."
          hint="쿠폰 관리에서 발급 마감일을 지정하거나, 상품의 「기간 할인 관리」에서 세일을 걸면 여기 막대로 보입니다."
        />
      </template>
    </div>
  </section>
</template>
