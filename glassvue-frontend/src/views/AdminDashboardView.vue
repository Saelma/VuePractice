<script setup>
/**
 * 관리자 홈 (2026-08-03, 백로그 B-16).
 *
 * **왜 만들었나**: 관리 화면을 7/28~7/31 에 계속 쌓았는데(회원 관리·감사 이력·쿠폰 지정·강제 삭제)
 * `/admin` 경로 자체가 없어서, 관리자로 로그인해도 「관리 ▾」 드롭다운을 기억해야만 일을 시작할 수 있었다.
 * 게다가 `/admin` 을 주소창에 치면 404 규칙에 걸려 **`/products` 로 튕겼다**.
 *
 * **이 화면이 답하는 질문은 하나다 — "지금 내가 처리해야 할 게 뭔가?"**
 * 그래서 배치가 ①처리 대기 → ②재고 부족 목록 → ③매출 한 줄 → ④바로가기 순이다.
 *
 * ⚠ **매출을 여기서 다시 그리지 않는다.** `/admin/stats`(2026-07-24, C-11)가 이미 요약 카드·30일 추이·
 * 상품별 TOP 을 그린다. 같은 걸 두 화면이 그리면 어느 쪽이 최신인지 헷갈리고 둘 다 고쳐야 한다 —
 * 여기선 **오늘·이번 달 숫자 한 줄**만 두고 나머지는 그 화면으로 보낸다.
 *
 * ⚠ **세 API 중 하나가 실패해도 나머지는 보여준다.** 대시보드는 여러 출처를 모으는 화면이라
 * 하나가 죽었다고 통째로 빈 화면이 되면 "관리자가 갈 곳이 없다"는 원래 문제로 돌아간다
 * (주문 목록이 요약 실패를 삼키는 것과 같은 판단).
 */
import { computed, onMounted, ref } from 'vue';
import { fetchAdminOrderCounts } from '../api/order';
import { fetchSalesOverview } from '../api/stats';
import { fetchLowStock, priceText } from '../api/product';
import EmptyState from '../components/EmptyState.vue';

const counts = ref(null);
const sales = ref(null);
const lowStock = ref(null);
const loading = ref(true);

/**
 * 부분 실패를 화면에 표시할 이름들. 배열이 비면 아무 말도 안 한다.
 * ⚠ 조용히 0을 보여주면 **"할 일이 없다"로 읽힌다** — 못 읽은 것과 없는 것은 다르다.
 */
const failed = ref([]);

/**
 * 관리자가 손대야 하는 주문 상태는 둘뿐이다.
 *
 * ⚠ `ORDERED`(결제 대기)는 **고객이 할 일**이라 넣지 않는다 — 관리자가 아무것도 할 수 없는 줄이
 * "처리 대기"에 섞이면 숫자가 매일 의미 없이 커진다.
 */
const pending = computed(() => [
  {
    key: 'PAID',
    label: '발송 대기',
    hint: '결제가 끝나 보내야 하는 주문',
    value: counts.value?.PAID ?? 0,
    to: { path: '/admin/orders', query: { status: 'PAID' } },
    unavailable: counts.value === null,
  },
  {
    key: 'RETURN_REQUESTED',
    label: '반품 요청',
    hint: '승인·거절을 기다리는 주문',
    value: counts.value?.RETURN_REQUESTED ?? 0,
    to: { path: '/admin/orders', query: { status: 'RETURN_REQUESTED' } },
    unavailable: counts.value === null,
  },
  {
    key: 'LOW_STOCK',
    label: '재고 부족',
    // ⚠ 기준값 문구를 화면이 짓지 않는다 — 서버가 준 threshold 를 그대로 읽는다.
    hint: lowStock.value ? `${lowStock.value.threshold}개 이하 남은 옵션` : '재고가 얼마 안 남은 옵션',
    value: lowStock.value?.count ?? 0,
    to: null, // 관리자용 재고 목록 화면은 없다 — 아래 목록의 줄마다 상품으로 보낸다
    unavailable: lowStock.value === null,
  },
]);

/** 처리할 게 정말 없는가 — 셋 다 읽혔고 셋 다 0일 때만 참이다(못 읽은 건 0이 아니다). */
const allClear = computed(() =>
  pending.value.every((p) => !p.unavailable && p.value === 0));

onMounted(async () => {
  // 셋을 함께 띄우고 각각의 실패를 따로 삼킨다(allSettled — 하나가 죽어도 나머지가 온다).
  const [c, s, l] = await Promise.allSettled([
    fetchAdminOrderCounts(),
    fetchSalesOverview(),
    fetchLowStock(),
  ]);
  if (c.status === 'fulfilled') counts.value = c.value; else failed.value.push('주문 건수');
  if (s.status === 'fulfilled') sales.value = s.value; else failed.value.push('매출');
  if (l.status === 'fulfilled') lowStock.value = l.value; else failed.value.push('재고');
  loading.value = false;
});

/** 관리 화면 바로가기. 「관리 ▾」에 없는 것(카테고리·상품 등록)도 여기선 보인다. */
const shortcuts = [
  { to: '/admin/orders', label: '주문 관리', hint: '발송·반품 처리' },
  { to: '/admin/members', label: '회원 관리', hint: '정지·등급·강제 탈퇴' },
  { to: '/admin/coupons', label: '쿠폰', hint: '발급·가입 쿠폰 지정' },
  { to: '/admin/marketing', label: '마케팅 발송', hint: '동의 회원에게 알림' },
  { to: '/admin/categories', label: '카테고리', hint: '분류 추가·수정' },
  { to: '/products/new', label: '상품 등록', hint: '새 상품 올리기' },
  { to: '/admin/stats', label: '매출 통계', hint: '추이·상품별 TOP' },
];
</script>

<template>
  <section class="page">
    <div class="mb-5">
      <h1 class="page-title">관리자 홈</h1>
      <p class="muted mt-1">지금 처리해야 할 것부터 보여줍니다.</p>
    </div>

    <!-- 부분 실패 — 숫자가 0인 것과 못 읽은 것을 구분해 말한다 -->
    <p v-if="failed.length" class="alert-warning mb-5">
      {{ failed.join(' · ') }} 정보를 불러오지 못했습니다. 해당 항목의 숫자는 정확하지 않을 수 있어요.
    </p>

    <!-- ① 처리 대기 -->
    <h2 class="section-title mb-3">처리 대기</h2>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-3">
      <div v-for="n in 3" :key="n" class="card space-y-3 p-5">
        <div class="skeleton h-4 w-20"></div>
        <div class="skeleton h-9 w-16"></div>
      </div>
    </div>

    <div v-else class="grid gap-4 sm:grid-cols-3">
      <component
        :is="item.to ? 'RouterLink' : 'div'"
        v-for="item in pending"
        :key="item.key"
        :to="item.to"
        class="card p-5"
        :class="item.to ? 'card-link block' : ''"
      >
        <p class="muted">{{ item.label }}</p>
        <p
          class="mt-1 text-3xl font-bold tabular-nums"
          :class="item.value > 0 ? 'text-ink-900' : 'text-ink-500'"
        >
          <!-- 못 읽었으면 0 대신 —. 숫자를 지어내지 않는다. -->
          {{ item.unavailable ? '—' : item.value }}
        </p>
        <p class="muted mt-2">{{ item.hint }}</p>
      </component>
    </div>

    <EmptyState
      v-if="!loading && allClear"
      class="mt-2"
      density="section"
      icon="✅"
      message="지금 처리할 것이 없어요."
      hint="발송 대기·반품 요청·재고 부족이 모두 0건입니다."
    />

    <!-- ② 재고 부족 목록 — 숫자만 주고 끝내면 결국 상품을 다시 찾아야 한다 -->
    <div v-if="lowStock && lowStock.count > 0" class="card mt-8 p-5">
      <div class="flex flex-wrap items-baseline justify-between gap-2">
        <h2 class="section-title">재고 부족</h2>
        <span class="muted">
          {{ lowStock.threshold }}개 이하 · 전체 {{ lowStock.count }}건
          <template v-if="lowStock.count > lowStock.items.length">
            (재고 적은 순 {{ lowStock.items.length }}건 표시)
          </template>
        </span>
      </div>

      <ul class="mt-3 divide-y divide-line border-t border-line">
        <li v-for="item in lowStock.items" :key="`${item.productId}-${item.variantName}`">
          <RouterLink
            :to="`/products/${item.productId}`"
            class="flex items-center justify-between gap-3 py-3 transition-colors hover:bg-canvas"
          >
            <span class="min-w-0">
              <span class="block truncate text-sm text-ink-900">{{ item.productName }}</span>
              <span class="muted">{{ item.variantName }}</span>
            </span>
            <span class="badge shrink-0" :class="item.stock === 0 ? 'badge-danger' : 'badge-warning'">
              {{ item.stock === 0 ? '품절' : `${item.stock}개 남음` }}
            </span>
          </RouterLink>
        </li>
      </ul>
    </div>

    <!-- ③ 매출 한 줄 — 자세한 건 /admin/stats 가 그린다 -->
    <div v-if="sales" class="card mt-8 p-5">
      <div class="flex flex-wrap items-baseline justify-between gap-2">
        <h2 class="section-title">매출</h2>
        <RouterLink to="/admin/stats" class="btn btn-secondary btn-sm">자세히 보기</RouterLink>
      </div>
      <dl class="mt-3 grid gap-4 border-t border-line pt-3 sm:grid-cols-2">
        <div v-for="row in [{ label: '오늘', v: sales.today }, { label: '이번 달', v: sales.thisMonth }]"
             :key="row.label">
          <dt class="muted">{{ row.label }} 상품매출</dt>
          <dd class="mt-1 text-xl font-bold tabular-nums text-ink-900">
            {{ priceText(row.v.itemSales) }}
            <span class="ml-1 text-sm font-normal text-ink-500">· 주문 {{ row.v.orderCount }}건</span>
          </dd>
        </div>
      </dl>
      <p class="muted mt-3">결제 완료(결제·발송·배송완료) 기준, 한국 시간. 취소된 주문은 제외됩니다.</p>
    </div>

    <!-- ④ 바로가기 -->
    <h2 class="section-title mb-3 mt-8">관리 화면</h2>
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <RouterLink v-for="s in shortcuts" :key="s.to" :to="s.to" class="card card-link block p-4">
        <span class="block text-sm font-medium text-ink-900">{{ s.label }}</span>
        <span class="muted mt-0.5 block">{{ s.hint }}</span>
      </RouterLink>
    </div>
  </section>
</template>
