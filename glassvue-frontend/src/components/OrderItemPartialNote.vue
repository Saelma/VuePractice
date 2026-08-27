<script setup>
/**
 * 품목 하나의 «부분» 흔적 — 「3개 중 1개 취소됨」 · 「2개 반품 요청됨」 세 줄.
 *
 * 🔴 **상세 화면에서 뽑아낸 것이다**(2026-08-27, BACKLOG §I-7). 뽑아낸 이유는 목록에도 같은
 * 표시가 필요해서인데, **베껴 쓰면 그 사본이 낡는다** — 이 저장소가 그걸로 두 번 데었다
 * (§I-1 매출 식 넷 · §I-2 목록 문구). 상세와 목록이 **같은 주문에 다른 말을 하지 않게** 하는 것이
 * 이 컴포넌트의 존재 이유다.
 *
 * ⚠ **원본 수량을 지우지 않는다** — 「3개 중 1개」가 읽히려면 `quantity` 와 `cancelledQuantity` 가
 *   둘 다 필요하다. 그래서 서버도 `quantity` 를 안 깎고 내려준다(OrderItemResponse).
 * ⚠ **취소와 반품은 줄을 나눈다** — 「1개 취소 · 1개 반품」이 한 줄로 합쳐지면 무엇이 왜 빠졌는지
 *   못 읽는다(G-10 결정 1, 서버가 칸을 나눈 것과 같은 이유).
 * ⚠ **요청은 색을 가른다** — 아직 안 빠진 것이라 위 둘(`text-danger`)과 달리 `muted` 다.
 */
defineProps({
  /** `OrderItemResponse` 한 건 그대로. 필요한 칸만 읽는다. */
  item: { type: Object, required: true },
});
</script>

<template>
  <!-- 아무것도 안 빠졌으면 줄 자체가 안 그려진다(멀쩡한 주문에 빈 자리가 남지 않게). -->
  <p v-if="item.cancelledQuantity > 0" class="mt-1 text-xs text-danger">
    <template v-if="item.remainingQuantity === 0 && item.returnedQuantity === 0">전량 취소됨</template>
    <template v-else>{{ item.quantity }}개 중 <b>{{ item.cancelledQuantity }}개</b> 취소됨</template>
  </p>
  <p v-if="item.returnedQuantity > 0" class="mt-1 text-xs text-danger">
    <template v-if="item.remainingQuantity === 0 && item.cancelledQuantity === 0">전량 반품됨</template>
    <template v-else>{{ item.quantity }}개 중 <b>{{ item.returnedQuantity }}개</b> 반품됨</template>
  </p>
  <p v-if="item.returnRequestedQuantity > 0" class="muted mt-1 text-xs">
    <b>{{ item.returnRequestedQuantity }}개</b> 반품 요청됨 (승인 대기)
  </p>
</template>
