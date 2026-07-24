<script setup>
/**
 * 적립금 · 회원 등급 (2026-07-24, 백로그 C-10).
 *
 * 다음 등급까지 남은 금액은 **서버가 계산해** 준다 — 화면이 등급 임계값을 알 필요가 없다
 * (쿠폰의 discountPreview와 같은 판단). 등급 정책이 바뀌어도 이 화면은 안 고친다.
 */
import { ref, computed, onMounted } from 'vue';
import { fetchPointAccount, fetchPointHistory, gradeText } from '../api/point';
import { priceText } from '../api/product';

const account = ref(null);
const history = ref([]);
const loading = ref(true);
const error = ref('');

/** 다음 등급까지 얼마나 왔는지(%). 최고 등급이면 100%. */
const progress = computed(() => {
  const a = account.value;
  if (!a) return 0;
  if (a.grade === a.nextGrade) return 100;
  const total = a.totalPurchase + a.amountToNextGrade;
  return total <= 0 ? 0 : Math.min(100, Math.round((a.totalPurchase / total) * 100));
});
const isTopGrade = computed(() => account.value && account.value.grade === account.value.nextGrade);

onMounted(async () => {
  try {
    account.value = await fetchPointAccount();
    // 이력은 부가 정보라 실패해도 잔액·등급은 보여준다.
    history.value = (await fetchPointHistory({ size: 10 }).catch(() => null))?.content ?? [];
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="card flex flex-col gap-3 p-5">
    <h2 class="section-title">적립금 · 등급</h2>
    <p v-if="error" class="alert-error">{{ error }}</p>

    <div v-if="loading" class="space-y-3">
      <div class="skeleton h-8 w-40"></div>
      <div class="skeleton h-3 w-full"></div>
    </div>

    <template v-else-if="account">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p class="muted">사용 가능한 적립금</p>
          <p class="text-2xl font-bold tabular-nums text-ink-900">{{ priceText(account.balance) }}</p>
        </div>
        <div class="text-right">
          <span class="badge badge-neutral">{{ gradeText(account.grade) }}</span>
          <p class="muted mt-1">구매 시 {{ account.earnPercent }}% 적립</p>
        </div>
      </div>

      <!-- 등급 진행 -->
      <div class="mt-1">
        <div class="h-2 w-full overflow-hidden rounded-full bg-canvas">
          <div class="h-full rounded-full bg-brand-600" :style="{ width: progress + '%' }"></div>
        </div>
        <p class="muted mt-1.5">
          누적 구매 <strong class="tabular-nums text-ink-700">{{ priceText(account.totalPurchase) }}</strong>
          <template v-if="!isTopGrade">
            · {{ gradeText(account.nextGrade) }}까지
            <strong class="tabular-nums text-ink-700">{{ priceText(account.amountToNextGrade) }}</strong>
          </template>
          <template v-else>· 최고 등급이에요</template>
        </p>
      </div>

      <p class="text-sm text-ink-700">
        적립금은 <strong>배송이 완료되면</strong> 지급됩니다. 주문서에서 결제할 때 쓸 수 있어요.
      </p>

      <!-- 이력 -->
      <div class="mt-2 border-t border-line pt-3">
        <h3 class="mb-2 text-sm font-medium text-ink-900">최근 내역</h3>
        <p v-if="!history.length" class="muted py-2">아직 적립금 내역이 없어요.</p>
        <ul v-else class="divide-y divide-line">
          <li v-for="h in history" :key="h.id" class="flex items-center gap-3 py-2">
            <span class="min-w-0 flex-1">
              <span class="block truncate text-sm text-ink-900">{{ h.reason }}</span>
              <span class="muted">{{ new Date(h.createdAt).toLocaleString('ko-KR') }}</span>
            </span>
            <!-- amount는 부호 있는 값이다(적립 +, 사용 −) — 화면이 부호를 다시 만들지 않는다 -->
            <span
              class="shrink-0 text-sm font-medium tabular-nums"
              :class="h.amount >= 0 ? 'text-emerald-700' : 'text-danger'"
            >{{ h.amount >= 0 ? '+' : '−' }}{{ priceText(Math.abs(h.amount)) }}</span>
            <span class="muted w-20 shrink-0 text-right tabular-nums">{{ priceText(h.balanceAfter) }}</span>
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>
