<script setup>
/**
 * 이벤트 쿠폰 배너 (2026-08-13, BACKLOG G-8) — **로그인한 사람에게만** 보인다.
 *
 * 🔴 **이 배너가 기능의 절반이다.** 요점이 "이 날 새 쿠폰이 나온다"를 미리 알리는 것이라,
 * 알림이 없으면 그 날 우연히 들어온 사람만 받는다. **예고가 곧 기능이다.**
 *
 * 배너가 말하는 것은 둘 중 하나이고 **섞지 않는다**:
 * - 오늘이 이벤트 날 → 「받기」(또는 「받음」). 발급은 누를 때만 일어난다.
 * - 이벤트가 앞에 있음 → "다음 이벤트 D-3". **행동을 요구하지 않는다.**
 *
 * ⚠ 비로그인에게는 이걸 안 보여준다(HomeView 가 로그인 여부로 가른다). 「받기」를 보여주면 누른 순간
 * 로그인으로 튕기고, 돌아왔을 때 이미 받은 상태라 **화면이 두 번 말을 바꾼다.** 비로그인에게는
 * 혜택 스트립에 **가입 유도 한 줄**로 따로 말한다 — 대상·문구·CTA 가 전부 다른 별개 배너다.
 *
 * ---------------------------------------------------------------- 모션 (DESIGN.md §6-1)
 *
 * 🔴 **이 프로젝트에 처음 들어가는 의도적 모션이다.** 그전까지는 transition-colors·카드 hover·
 * 스켈레톤뿐이었다(=연출용 모션 0). 그래서 여기서 정한 것이 기준이 된다 — DESIGN.md 에 적었다.
 *
 * 지킬 것 다섯(BACKLOG G-8):
 * ① 연출은 기다리는 시간을 **설명**하는 것이지 만드는 것이 아니다. 서버가 80ms 에 답하는데 연출이
 *    800ms 면 사용자는 720ms 를 **기다린 것**이다. → 응답을 기다리는 동안 돌고, 오면 완료로 전이한다.
 *    다만 «기대감» 몫으로 최소 400ms 는 잡는다(그 이상은 안 늘린다).
 * ② **실패 경로에도 모션이 있다.** 성공만 만들면 실패가 «멈춘 것»처럼 보인다.
 * ③ **연타를 막는다.** 서버는 유니크 제약으로 막지만, 화면이 「발급되었습니다」를 두 번 띄우면
 *    그건 거짓말이다.
 * ④ `prefers-reduced-motion: reduce` 면 **즉시 전환**한다(연출을 건너뛴다).
 * ⑤ **강조색은 CTA·상태에만.** 컨페티·색 폭죽은 이 톤에서 튄다.
 */
import { ref, computed, onMounted } from 'vue';
import { fetchEventCoupon, claimEventCoupon, couponDiscountText } from '../api/coupon';
import { priceText } from '../api/product';

const banner = ref(null);
/** idle | pending | done | failed — 버튼이 곧 상태다(별도 토스트를 띄우지 않는다). */
const phase = ref('idle');
const failMessage = ref('');

/** ④ 연출을 건너뛸지. 매체 질의는 마운트 시 한 번만 읽으면 충분하다(설정을 바꾸면 새로고침한다). */
const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

/** ① «기대감» 몫. 응답이 이보다 빨리 와도 여기까지는 연출을 보여주고, 늦게 오면 늘리지 않는다. */
const MIN_PENDING_MS = reduceMotion ? 0 : 400;

const claimed = computed(() => banner.value?.claimed || phase.value === 'done');

const label = computed(() => {
  if (claimed.value) return '받음';
  if (phase.value === 'pending') return '받는 중';
  return '쿠폰 받기';
});

onMounted(load);

async function load() {
  try {
    banner.value = await fetchEventCoupon();
  } catch (e) {
    banner.value = null; // 배너 실패가 홈을 망가뜨리지 않는다 — 자리를 안 만들 뿐이다.
  }
}

async function claim() {
  // ③ 연타 잠금 — 도는 동안·끝난 뒤에는 아무 일도 하지 않는다.
  if (phase.value === 'pending' || claimed.value) return;
  phase.value = 'pending';
  failMessage.value = '';

  const started = Date.now();
  try {
    await claimEventCoupon();
    await holdMinimum(started);
    phase.value = 'done';
  } catch (e) {
    await holdMinimum(started);
    if (e.code === 'COUPON-409I') {
      // 실패가 아니라 **상태 확정**이다 — 다른 탭에서 이미 받았을 때 정확히 이 답이 온다.
      phase.value = 'done';
      return;
    }
    // ② 되돌아오는 모션 + 이유. 발급 창이 닫혔으면 배너 자체가 낡았으니 다시 읽는다.
    phase.value = 'failed';
    failMessage.value = e.message || '쿠폰을 받지 못했어요.';
    if (e.code === 'COUPON-400C') await load();
    window.setTimeout(() => {
      if (phase.value === 'failed') phase.value = 'idle';
    }, 2400);
  }
}

function holdMinimum(started) {
  const remaining = MIN_PENDING_MS - (Date.now() - started);
  return remaining > 0 ? new Promise((resolve) => window.setTimeout(resolve, remaining)) : Promise.resolve();
}

/** 날짜 표기는 화면 쪽 관심사 — 서버는 시각만 준다(HomeView 의 fmtDate 와 같은 결). */
const fmtDay = (iso) =>
  (iso ? new Date(iso).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' }) : '');
const fmtTime = (iso) =>
  (iso ? new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : '');
</script>

<template>
  <!-- 줄 게 없으면 자리를 만들지 않는다(빈 상태 문구도 없다) -->
  <section
    v-if="banner"
    class="rounded-card border border-line bg-canvas px-5 py-4"
  >
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div class="min-w-0">
        <!-- 오늘 진행 중 -->
        <template v-if="banner.open">
          <p class="text-sm text-ink-700">
            <strong class="text-ink-900">{{ banner.name }}</strong>
            — <strong class="text-ink-900">{{ couponDiscountText(banner) }}</strong>
            <span v-if="banner.minOrderAmount"> · {{ priceText(banner.minOrderAmount) }} 이상</span>
          </p>
          <p class="mt-1 text-xs text-ink-500">
            <template v-if="claimed">쿠폰함에 담겼어요. {{ fmtDay(banner.validUntil) }}까지 쓸 수 있어요.</template>
            <template v-else>오늘 {{ fmtTime(banner.issueUntil) }}까지 받을 수 있어요.</template>
          </p>
        </template>

        <!-- 예고 — 행동을 요구하지 않는다 -->
        <template v-else>
          <p class="text-sm text-ink-700">
            다음 이벤트 <strong class="tabular-nums text-ink-900">D-{{ banner.daysUntil }}</strong>
            · <strong class="text-ink-900">{{ couponDiscountText(banner) }}</strong> 쿠폰
          </p>
          <p class="mt-1 text-xs text-ink-500">{{ fmtDay(banner.validFrom) }}에 열려요.</p>
        </template>
      </div>

      <!-- 오늘 진행 중일 때만 버튼. 예고에는 버튼이 없다(누를 것이 없다). -->
      <div v-if="banner.open" class="flex shrink-0 flex-col items-start gap-1 sm:items-end">
        <button
          type="button"
          class="btn btn-primary claim-btn"
          :class="{ 'is-pending': phase === 'pending', 'is-done': claimed, 'is-failed': phase === 'failed' }"
          :disabled="phase === 'pending' || claimed"
          @click="claim"
        >
          <!-- 스피너 → 체크. 라벨이 함께 바뀌므로 아이콘은 보조다(색·모양만으로 말하지 않는다) -->
          <span v-if="phase === 'pending'" class="claim-spinner" aria-hidden="true"></span>
          <svg v-else-if="claimed" class="claim-check" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M3 8.5 L6.5 12 L13 4.5" fill="none" stroke="currentColor" stroke-width="2"
                  stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          {{ label }}
        </button>
        <!-- ② 실패에도 말이 있다. role=status 로 읽어 주되 자리를 늘 차지하지는 않는다. -->
        <p v-if="phase === 'failed'" role="status" class="text-xs text-danger">{{ failMessage }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
/*
 * 토스식 마이크로 인터랙션 — **누르는 순간 살짝 눌리고, 끝나면 조용히 확정된다.**
 * 색으로 축하하지 않는다(무채색 원칙). 움직임의 크기는 1~2px·150ms 수준으로 절제한다.
 */
.claim-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  transition: transform 150ms ease, opacity 150ms ease, background-color 150ms ease;
}
.claim-btn:active:not(:disabled) {
  transform: scale(0.97); /* 누르는 순간 — 이 1px 이 «반응했다»의 전부다 */
}
.claim-btn.is-pending {
  opacity: 0.85;
}
.claim-btn.is-done {
  /* 확정은 «성공색»이 아니라 «가라앉음»으로 말한다 — 더 이상 누를 것이 없다는 뜻이다. */
  background-color: var(--color-ink-500);
  border-color: var(--color-ink-500);
  opacity: 1;
}
/* ② 실패는 되돌아온다 — 흔들지 않고 한 번 물러섰다 제자리로. */
.claim-btn.is-failed {
  animation: claim-recoil 240ms ease;
}

.claim-spinner {
  width: 0.875rem;
  height: 0.875rem;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 9999px;
  animation: claim-spin 600ms linear infinite;
}

/* 체크는 «그려진다» — 나타나는 것보다 확정의 느낌이 분명하다. */
.claim-check {
  width: 0.875rem;
  height: 0.875rem;
}
.claim-check path {
  stroke-dasharray: 20;
  stroke-dashoffset: 20;
  animation: claim-draw 260ms ease forwards;
}

@keyframes claim-spin {
  to { transform: rotate(360deg); }
}
@keyframes claim-draw {
  to { stroke-dashoffset: 0; }
}
@keyframes claim-recoil {
  0% { transform: translateX(0); }
  40% { transform: translateX(-3px); }
  100% { transform: translateX(0); }
}

/* ④ 연출을 끈 사람에게는 결과만 준다. 상태는 라벨·색으로 그대로 전달된다. */
@media (prefers-reduced-motion: reduce) {
  .claim-btn,
  .claim-btn.is-failed,
  .claim-spinner,
  .claim-check path {
    animation: none;
    transition: none;
  }
  .claim-check path {
    stroke-dashoffset: 0;
  }
}
</style>
