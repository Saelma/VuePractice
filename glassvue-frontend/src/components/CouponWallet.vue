<script setup>
/**
 * 내 쿠폰함 (혜택 허브, 2026-07-28). 지금까지 쿠폰은 주문서에서만 보였다 — 보유 쿠폰을 확인할 곳이 없었다.
 * 주문 맥락이 없어 itemsTotal=0 으로 부르고(그래서 usable/discountPreview 는 안 쓴다), 쿠폰의 본질 속성
 * (할인·최소주문·만료일)만 보여준다. 실제 사용은 주문서에서(그때 서버가 usable·할인액을 계산).
 */
import { ref, onMounted } from 'vue';
import { fetchMyCoupons, couponDiscountText } from '../api/coupon';
import { priceText } from '../api/product';
import EmptyState from './EmptyState.vue';

const coupons = ref([]);
const loading = ref(true);
const error = ref('');

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleDateString('ko-KR') : '');

// 만료 7일 이내면 임박 표시(대략 — 자정 경계까지 따지지 않는다).
const DAY = 24 * 60 * 60 * 1000;
function daysLeft(iso) {
  if (!iso) return null;
  return Math.ceil((new Date(iso).getTime() - Date.now()) / DAY);
}

onMounted(async () => {
  try {
    coupons.value = await fetchMyCoupons(0);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="card flex flex-col gap-3 p-5">
    <div class="flex items-center gap-3">
      <h2 class="section-title">쿠폰함</h2>
      <span v-if="!loading && coupons.length" class="badge badge-neutral tabular-nums">{{ coupons.length }}장</span>
    </div>
    <p v-if="error" class="alert-error">{{ error }}</p>

    <div v-if="loading" class="space-y-2">
      <div v-for="n in 2" :key="n" class="skeleton h-16 w-full rounded-card"></div>
    </div>

    <ul v-else-if="coupons.length" class="space-y-2">
      <li
        v-for="c in coupons"
        :key="c.id"
        class="flex items-center justify-between gap-4 rounded-card border border-line p-4"
      >
        <div class="min-w-0">
          <p class="truncate text-sm font-medium text-ink-900">{{ c.name }}</p>
          <p class="muted mt-0.5 tabular-nums">
            {{ c.minOrderAmount ? priceText(c.minOrderAmount) + ' 이상' : '금액 조건 없음' }}
            · ~{{ fmtDate(c.validUntil) }}까지
          </p>
        </div>
        <div class="shrink-0 text-right">
          <p class="text-lg font-bold tabular-nums text-ink-900">{{ couponDiscountText(c) }}</p>
          <span v-if="daysLeft(c.validUntil) !== null && daysLeft(c.validUntil) <= 7"
                class="badge badge-warning mt-1 tabular-nums">
            {{ daysLeft(c.validUntil) <= 0 ? '오늘 만료' : `${daysLeft(c.validUntil)}일 남음` }}
          </span>
        </div>
      </li>
    </ul>

    <EmptyState v-else icon="🎟️" message="보유한 쿠폰이 없어요." />
  </div>
</template>
