<script setup>
/**
 * 별점 표시·입력 공용 컴포넌트.
 *
 * 이전엔 ProductReviews.vue 안에 ①작성 폼 입력 ②목록 항목 표시 ③요약 표시 세 벌이
 * 따로 있었다. 상품 목록·상세에도 별점을 붙이면서 다섯 벌이 될 판이라 하나로 모았다.
 *
 * - editable=false(기본): 읽기 전용. 정수 별점이면 채운 별로, 소수(평균)면 숫자로 보여준다.
 * - editable=true: 클릭으로 1~5 선택. v-model로 값을 올린다.
 */
import { computed } from 'vue';

const props = defineProps({
  modelValue: { type: Number, default: 0 },
  editable: { type: Boolean, default: false },
  /** 리뷰 개수. 넘기면 별 옆에 (n)으로 표시한다. */
  count: { type: Number, default: null },
  size: { type: String, default: 'base' }, // 'sm' | 'base' | 'lg'
});
const emit = defineEmits(['update:modelValue']);

const sizeClass = computed(() => ({
  sm: 'text-sm',
  base: 'text-base',
  lg: 'text-2xl',
}[props.size] || 'text-base'));

/** 평균처럼 소수가 섞인 값은 별 개수로 표현하지 않고 숫자를 함께 보여준다. */
const isFractional = computed(() => !Number.isInteger(props.modelValue));

/** 리뷰가 0개면 "별점 0점"이 아니라 "아직 없음"이다 — 비정규화 기본값 0과 구분해야 한다. */
const hasNoReview = computed(() => props.count === 0);

const filled = computed(() => Math.round(props.modelValue));
</script>

<template>
  <!-- 입력형 -->
  <div v-if="editable" class="flex" role="radiogroup" aria-label="별점">
    <button
      v-for="n in 5"
      :key="n"
      type="button"
      class="leading-none"
      :class="[sizeClass, n <= modelValue ? 'text-amber-400' : 'text-slate-300']"
      :aria-label="`${n}점`"
      :aria-checked="n === modelValue"
      role="radio"
      @click="emit('update:modelValue', n)"
    >★</button>
  </div>

  <!-- 리뷰 없음 -->
  <span v-else-if="hasNoReview" class="text-slate-400" :class="sizeClass">리뷰 없음</span>

  <!-- 평균(소수) — 별 하나 + 숫자 -->
  <span v-else-if="isFractional" class="text-amber-500" :class="sizeClass">
    <span class="font-semibold">★ {{ modelValue.toFixed(1) }}</span>
    <span v-if="count !== null" class="ml-1 text-slate-500" :class="size === 'lg' ? 'text-sm' : 'text-xs'">({{ count }})</span>
  </span>

  <!-- 개별 별점(정수) — 채운 별 -->
  <span v-else class="text-amber-400" :class="sizeClass">
    {{ '★'.repeat(filled) }}<span class="text-slate-300">{{ '★'.repeat(5 - filled) }}</span>
    <span v-if="count !== null" class="ml-1 text-slate-500" :class="size === 'lg' ? 'text-sm' : 'text-xs'">({{ count }})</span>
  </span>
</template>
