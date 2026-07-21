<script setup>
/**
 * 목록형 로딩 스켈레톤 — 카드 안에 행을 늘어놓는 형태(공지 목록·리뷰·문의가 같은 모양).
 *
 * 상세 화면(상품 상세·장바구니·주문 상세 등)의 스켈레톤은 각자 레이아웃을 흉내 내야 해서
 * 일반화하면 오히려 어색해진다 → 그쪽은 `.skeleton` 유틸리티로 직접 조립한다(DESIGN.md §5).
 */
defineProps({
  rows: { type: Number, default: 3 },
  /** 행 오른쪽에 값 하나가 붙는 목록(조회수·금액 등)이면 true */
  trailing: { type: Boolean, default: false },
});
</script>

<template>
  <div class="card divide-y divide-line" aria-hidden="true">
    <div
      v-for="n in rows"
      :key="n"
      class="flex items-center justify-between gap-4 px-5 py-4"
    >
      <div class="flex-1 space-y-2">
        <div class="skeleton h-4 w-2/3"></div>
        <div class="skeleton h-3 w-32"></div>
      </div>
      <div v-if="trailing" class="skeleton h-3 w-16"></div>
    </div>
  </div>
</template>
