<script setup>
/**
 * 관리자 매출 대시보드 (2026-07-24, 백로그 C-11).
 *
 * 화면이 답해야 하는 질문 순서대로 배치한다:
 *   ① 오늘·이번달 얼마 벌었나(요약 카드) → ② 흐름이 어떤가(일별 추이) → ③ 뭐가 팔리나(상품별 TOP)
 *
 * ⚠ **상품매출과 배송비를 합쳐서 보여주지 않는다.** 배송비는 그대로 택배비로 나가는 돈이라
 * 상품매출에 섞으면 장사가 잘되는지 알 수 없어진다(서버도 같은 이유로 나눠서 준다).
 */
import { ref, computed, onMounted } from 'vue';
import {
  fetchSalesOverview, barHeight, shortDate, PRESETS, presetRange, matchedPreset,
} from '../api/stats';
import { priceText } from '../api/product';
import EmptyState from '../components/EmptyState.vue';

const data = ref(null);
const loading = ref(true);
const error = ref('');
/** 커서가 올라간 날. 값은 차트 헤더에 표시한다(막대 옆 툴팁은 잘린다 — 템플릿 주석 참조). */
const hovered = ref(null);

const maxDaily = computed(() => {
  if (!data.value?.daily?.length) return 0;
  return Math.max(...data.value.daily.map((d) => d.itemSales));
});
const hasAnySales = computed(() => (data.value?.allTime?.orderCount ?? 0) > 0);

/*
 * 기간 선택 (B-26, 2026-08-13).
 *
 * 🔴 **화면은 날짜만 보내고 경계는 서버가 만든다.** 「그 날의 00:00 이 언제인가」를 여기서 계산하면
 * KST 경계가 두 곳에 생기고, **하루가 어긋나도 화면은 멀쩡해 보인다.**
 *
 * 🔴 **「오늘」의 기준도 브라우저 시계가 아니다.** 첫 조회는 파라미터 없이 부르고, 서버가 돌려준
 * `to`(= KST 오늘)를 프리셋 계산의 기준으로 삼는다 — 장부는 KST 인데 `new Date()` 는 보는 사람의
 * 시간대를 따르기 때문이다.
 */
const from = ref('');
const to = ref('');
/** 서버가 알려 준 KST 오늘. 프리셋 계산의 유일한 기준점이다. */
const todayKst = ref('');
const activePreset = computed(() => matchedPreset(from.value, to.value, todayKst.value));

onMounted(() => load());

async function load(params) {
  loading.value = true;
  error.value = '';
  try {
    const res = await fetchSalesOverview(params);
    data.value = res;
    // 서버가 **실제로 집계한 구간**을 화면 상태로 받아 온다(내가 보낸 값이 아니라).
    from.value = res.from;
    to.value = res.to;
    // 파라미터 없이 부른 첫 응답의 `to` 가 곧 KST 오늘이다.
    if (!todayKst.value && !params) todayKst.value = res.to;
  } catch (e) {
    // ⚠ 기간이 틀렸다는 답(STATS-400P·400L)도 여기로 온다 — 화면은 서버 문구를 그대로 띄운다.
    //    ⚠ data 는 지우지 않는다: 직전에 보던 숫자를 남겨 둬야 «무엇을 고쳤다 실패했는지» 가 보인다.
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function applyPreset(key) {
  const range = presetRange(key, todayKst.value);
  load(range);
}

/** 날짜 칸을 직접 고쳤을 때. ⚠ 둘 다 채워졌을 때만 부른다(한쪽만 바뀐 중간 상태로 조회하지 않는다). */
function applyManual() {
  if (from.value && to.value) load({ from: from.value, to: to.value });
}
</script>

<template>
  <section class="page">
    <div class="mb-5">
      <h1 class="page-title">매출 통계</h1>
      <p class="muted mt-1">
        결제 완료된 주문(결제·발송·배송완료) 기준이며, 날짜는 한국 시간입니다.
        취소된 주문은 제외됩니다.
      </p>
    </div>

    <!--
      기간 선택 (B-26). 🔴 **관리자 기간 선택은 네이티브 date 2칸 + 프리셋**으로 통일한다(DESIGN §7)
      — 이 화면에는 DevExtreme 이 하나도 안 쓰였고, DX 컨트롤을 넣으면 그 테마까지 따라 들어온다.
      ⚠ 프리셋이 먼저다: 실무에서 묻는 것 대부분이 「지난 달」·「30일」이고, 달력을 두 번 찍는 것보다 빠르다.
    -->
    <div class="card mb-5 flex flex-wrap items-end gap-x-4 gap-y-3 p-4">
      <label class="field">
        <span class="field-label">시작일</span>
        <input v-model="from" type="date" class="ipt" :max="to || undefined" @change="applyManual" />
      </label>
      <label class="field">
        <span class="field-label">종료일 (포함)</span>
        <input v-model="to" type="date" class="ipt" :min="from || undefined" @change="applyManual" />
      </label>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="p in PRESETS"
          :key="p.key"
          type="button"
          class="btn btn-secondary btn-sm"
          :class="activePreset === p.key ? 'border-ink-900 text-ink-900' : ''"
          :disabled="!todayKst"
          @click="applyPreset(p.key)"
        >{{ p.label }}</button>
      </div>
    </div>

    <p v-if="error" class="alert-error mb-5">{{ error }}</p>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-3">
      <div v-for="n in 3" :key="n" class="card space-y-3 p-5">
        <div class="skeleton h-4 w-16"></div>
        <div class="skeleton h-8 w-32"></div>
      </div>
    </div>

    <template v-else-if="data">
      <!-- ① 요약 -->
      <div class="grid gap-4 sm:grid-cols-3">
        <!--
          🔴 **기간을 따르는 카드와 안 따르는 카드를 갈라 적는다**(B-26).
          「지난 달」을 골라 놓고 「오늘」 카드가 지난달 어느 날을 가리키면 거짓말이 된다 —
          그래서 뒤 둘에는 «기간과 무관» 을 화면에 쓴다.
        -->
        <div v-for="card in [
              { label: `${data.from} ~ ${data.to}`, value: data.period, scoped: true },
              { label: '오늘', value: data.today, scoped: false },
              { label: '전체 누적', value: data.allTime, scoped: false },
            ]" :key="card.label" class="card p-5">
          <p class="muted">
            {{ card.label }} 상품매출
            <span v-if="!card.scoped" class="text-ink-400">· 기간과 무관</span>
          </p>
          <p class="mt-1 text-2xl font-bold tabular-nums text-ink-900">
            {{ priceText(card.value.itemSales) }}
          </p>
          <dl class="mt-3 space-y-1 border-t border-line pt-3 text-sm">
            <div class="flex justify-between gap-3">
              <dt class="text-ink-500">주문</dt>
              <dd class="tabular-nums text-ink-700">{{ card.value.orderCount }}건</dd>
            </div>
            <div class="flex justify-between gap-3">
              <dt class="text-ink-500">배송비 수입</dt>
              <dd class="tabular-nums text-ink-700">{{ priceText(card.value.shippingSales) }}</dd>
            </div>
            <div v-if="card.value.couponDiscount > 0" class="flex justify-between gap-3">
              <dt class="text-ink-500">쿠폰 할인</dt>
              <dd class="tabular-nums text-danger">−{{ priceText(card.value.couponDiscount) }}</dd>
            </div>
            <div class="flex justify-between gap-3">
              <dt class="text-ink-500">평균 주문금액</dt>
              <dd class="tabular-nums text-ink-700">{{ priceText(card.value.averageOrderAmount) }}</dd>
            </div>
          </dl>
        </div>
      </div>

      <EmptyState
        v-if="!hasAnySales"
        class="mt-8"
        icon="📉"
        message="아직 결제 완료된 주문이 없어요."
        hint="주문을 넣고 결제까지 진행하면 여기에 집계됩니다."
      />

      <template v-else>
        <!-- ② 일별 추이 -->
        <div class="card mt-8 p-5">
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <!-- ⚠ 「최근 30일」이 아니라 **고른 기간**이다. 제목이 안 따라가면 지난달을 보면서
                 「최근 30일」이라고 읽게 된다 — 숫자보다 제목이 거짓말하기 쉽다. -->
            <h2 class="section-title">{{ data.from }} ~ {{ data.to }} 상품매출</h2>
            <!--
              hover 값을 **막대 옆이 아니라 여기** 보여준다.
              막대 30칸이면 한 칸이 20px 남짓이라, 툴팁을 막대에 붙이면 양끝 칸에서 가로로 잘린다.
              게다가 차트가 overflow-x-auto 안에 있어서 막대 위로 띄우면 세로로도 잘린다
              (한 축이 visible이 아니면 다른 축도 auto가 되는 CSS 규칙 — 2026-07-24에 실제로 겪었다).
            -->
            <span v-if="hovered" class="text-sm tabular-nums text-ink-900">
              <strong>{{ hovered.date }}</strong>
              · {{ priceText(hovered.itemSales) }}
              · {{ hovered.orderCount }}건
              <span v-if="hovered.shippingSales > 0" class="text-ink-500">
                (배송비 {{ priceText(hovered.shippingSales) }})
              </span>
            </span>
            <!-- ⚠ 이 기간에 매출이 0이면 막대가 전부 바닥이라 «고장» 처럼 보인다 — 그렇다고 말해 준다. -->
            <span v-else-if="!data.period.orderCount" class="muted">이 기간에는 결제된 주문이 없습니다.</span>
            <span v-else class="muted">막대에 커서를 올리면 그날 매출이 보입니다 · 최대 {{ priceText(maxDaily) }}</span>
          </div>

          <!-- 막대는 CSS만으로 그린다. 차트 라이브러리를 하나 더 넣을 만한 복잡도가 아니다. -->
          <div class="mt-5 overflow-x-auto">
            <div class="flex min-w-[640px] items-end gap-1" style="height: 160px">
              <!--
                hover 대상은 막대가 아니라 **칸 전체**다. 매출 0인 날은 막대 높이가 0이라
                막대에만 걸면 커서를 올릴 수 없고, 낮은 막대도 조준하기 어렵다.
                터치에서도 되도록 click을 같이 받는다.
              -->
              <div
                v-for="d in data.daily"
                :key="d.date"
                class="flex flex-1 cursor-default flex-col items-center justify-end rounded-t transition-colors"
                :class="hovered?.date === d.date ? 'bg-brand-100' : 'hover:bg-canvas'"
                style="height: 100%"
                @mouseenter="hovered = d"
                @mouseleave="hovered = null"
                @click="hovered = d"
              >
                <!--
                  선택된 막대가 **진해져야** 한다. brand-700은 이 팔레트에서 hover용으로 더 밝은 색이라
                  (index.css 주석: "검정 버튼은 밝아지는 쪽이 자연스럽다") 그걸 쓰면 강조가 거꾸로 된다.
                  대신 불투명도로 가른다 — 평소 70%, 선택되면 100%.
                -->
                <div
                  class="w-full rounded-t bg-brand-600 transition-opacity"
                  :class="hovered?.date === d.date ? 'opacity-100' : 'opacity-70'"
                  :style="{ height: barHeight(d.itemSales, maxDaily) + '%' }"
                ></div>
              </div>
            </div>
            <div class="mt-2 flex min-w-[640px] gap-1">
              <span
                v-for="(d, i) in data.daily"
                :key="d.date"
                class="flex-1 text-center text-[10px] text-ink-400"
              >{{ i % 5 === 0 ? shortDate(d.date) : '' }}</span>
            </div>
          </div>
        </div>

        <!-- ③ 상품별 -->
        <div class="card mt-8 p-5">
          <h2 class="section-title">많이 팔린 상품 TOP {{ data.topProducts.length }}</h2>
          <p class="muted mt-1">
            판매액은 <strong>쿠폰 할인 전</strong> 금액입니다 — 쿠폰은 주문 단위라 상품별로 나눌 수 없어요.
            <br />
            취소·반품된 몫은 <strong>빠진 뒤</strong>의 수량·금액입니다.
            <br />
            ⚠ 상점의 「인기순」과는 <strong>세는 창이 다릅니다</strong> — 저쪽은 전체 기간 누적이라 숫자가 다를 수 있어요.
          </p>
          <!--
            ⚠ 기간을 열면서 **빈 목록이 흔해졌다**. 「팔린 게 없다」와 「이 기간에 안 팔렸다」는 다른 말이라
            문구를 가른다(DESIGN §5 빈 상태 · 7/20 교훈). 전체 매출이 0인 경우는 위 EmptyState 가 맡는다.
          -->
          <EmptyState
            v-if="!data.topProducts.length"
            icon="🗓️"
            message="이 기간에는 팔린 상품이 없어요."
            hint="기간을 넓히거나 다른 달을 골라 보세요."
          />
          <ul v-else class="mt-4 divide-y divide-line">
            <li v-for="(p, i) in data.topProducts" :key="p.productId" class="flex items-center gap-3 py-3">
              <span class="w-6 shrink-0 text-center text-sm font-semibold tabular-nums text-ink-400">
                {{ i + 1 }}
              </span>
              <span class="min-w-0 flex-1 truncate text-sm text-ink-900">{{ p.productName }}</span>
              <span class="shrink-0 text-sm tabular-nums text-ink-700">{{ p.quantity }}개</span>
              <span class="w-28 shrink-0 text-right text-sm font-medium tabular-nums text-ink-900">
                {{ priceText(p.sales) }}
              </span>
            </li>
          </ul>
        </div>
      </template>
    </template>
  </section>
</template>
