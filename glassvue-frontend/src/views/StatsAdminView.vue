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
import { fetchSalesOverview, barHeight, shortDate } from '../api/stats';
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

onMounted(async () => {
  try {
    data.value = await fetchSalesOverview();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
});
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
        <div v-for="card in [
              { label: '오늘', value: data.today },
              { label: '이번 달', value: data.thisMonth },
              { label: '전체', value: data.allTime },
            ]" :key="card.label" class="card p-5">
          <p class="muted">{{ card.label }} 상품매출</p>
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
            <h2 class="section-title">최근 30일 상품매출</h2>
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
          </p>
          <ul class="mt-4 divide-y divide-line">
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
