<script setup>
/**
 * 상품 카드 — 목록·홈에서 함께 쓴다(중복 방지). 원래 ProductListView 안에 있던 마크업을 뽑아 온 것이라
 * 두 화면의 카드가 갈리지 않는다(홈 재구성 B-8 에서 추출).
 *
 * 카드가 통째로 <button>이라 찜 하트를 그 안에 넣을 수 없다(button 중첩) → 래퍼 div에 절대배치한다.
 *
 * ⚠ **모든 카드의 높이가 같아야 한다**(2026-07-29 수정). 그리드에서 카드 높이가 제각각이면
 * 가격·별점 줄이 들쭉날쭉해 목록이 지저분해진다. 높이를 흔들던 원인이 셋이었다:
 *   ① 태그라인 유무  ② 품절 배지 유무  ③ 정가 취소선 유무(할인 상품만 한 줄 더)
 * 셋 다 "있으면 줄이 늘어나는" 구조라, 조건부 렌더링을 없애는 것만으로는 해결되지 않는다.
 * → 구조로 고정한다:
 *   · 카드를 `h-full flex flex-col` 로 만들어 **그리드 행 높이에 맞춰 늘린다**(기본 stretch 활용).
 *   · 가격·별점 줄에 `mt-auto` — **항상 카드 바닥에 붙는다.** 위쪽 내용이 몇 줄이든 무관.
 *   · 품절 배지는 **이미지 위 오버레이**로 옮겼다 — 텍스트 흐름에서 아예 빼는 게 확실하다
 *     (할인 배지가 이미 그렇게 하고 있어 방식도 일관된다).
 */
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { priceText, hasDiscount, discountRate, statusText } from '../api/product';
import StarRating from './StarRating.vue';
import WishlistButton from './WishlistButton.vue';

const props = defineProps({
  product: { type: Object, required: true },
});

const router = useRouter();
const thumbOf = (p) => (p.images && p.images.length ? p.images[0].thumbUrl : null);

/**
 * 품절/상태 라벨. 없으면 null.
 * 판매중이어도 전 옵션이 품절이면 품절로 본다(soldOut 은 서버가 계산).
 */
const stateLabel = computed(() => {
  const p = props.product;
  if (p.status !== 'SELLING') return statusText(p.status);
  return p.soldOut ? '품절' : null;
});
</script>

<template>
  <div class="relative h-full">
    <WishlistButton :product-id="product.id" class="absolute right-3 top-3 z-10" />
    <button
      type="button"
      class="group flex h-full w-full flex-col overflow-hidden rounded-card bg-surface text-left shadow-card transition duration-200 hover:-translate-y-0.5 hover:shadow-lift focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
      @click="router.push(`/products/${product.id}`)"
    >
      <div class="relative aspect-square shrink-0 overflow-hidden bg-canvas">
        <img
          v-if="thumbOf(product)"
          :src="thumbOf(product)"
          :alt="product.name"
          class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
        />
        <div v-else class="flex h-full items-center justify-center text-3xl text-ink-400">🖼️</div>

        <!-- 할인율은 이미지 좌상단 뱃지로(커머스 관례 — 컬리·29CM·무신사). 가격줄은 깔끔하게. -->
        <span
          v-if="hasDiscount(product)"
          class="absolute left-2 top-2 rounded-control bg-danger px-1.5 py-0.5 text-xs font-bold text-white tabular-nums"
        >{{ discountRate(product) }}%</span>

        <!-- 품절/판매중지 — 텍스트 흐름이 아니라 이미지 오버레이다(카드 높이를 흔들지 않는다). -->
        <div
          v-if="stateLabel"
          class="absolute inset-0 flex items-center justify-center bg-surface/70"
        >
          <span class="badge badge-warning">{{ stateLabel }}</span>
        </div>
      </div>

      <div class="flex flex-1 flex-col p-4">
        <p class="text-xs text-ink-500">{{ product.categoryName }}</p>
        <h3 class="mt-0.5 line-clamp-1 text-sm font-medium text-ink-900">{{ product.name }}</h3>
        <!--
          한 줄 카피(V33). 이름과 가격 사이 — "무엇이 좋은 상품인지"는 이름 다음에 읽혀야 한다.
          없으면 줄을 그리지 않는다(빈 줄은 지저분하다). 높이는 위 mt-auto 가 맞추므로 여기선 신경 쓰지 않는다.
          line-clamp-1 — 카피가 길어도 두 줄로 넘치지 않게(넘치면 카드 안 여백만 줄어든다).
        -->
        <p v-if="product.tagline" class="mt-1 line-clamp-1 text-xs text-ink-500">{{ product.tagline }}</p>
        <!-- mt-auto: 태그라인·취소선이 있든 없든 이 줄은 항상 카드 바닥에 온다 -->
        <div class="mt-auto flex items-end justify-between gap-2 pt-2">
          <div class="min-w-0">
            <!-- 할인율은 이미지 뱃지로 옮겼다. 여기선 정가 취소선(위) + 판매가(아래)만. 정가 없으면 판매가만. -->
            <span v-if="hasDiscount(product)" class="muted block tabular-nums line-through">{{ priceText(product.listPrice) }}</span>
            <span class="text-lg font-semibold tabular-nums text-ink-900">{{ priceText(product.price) }}</span>
          </div>
          <StarRating :model-value="product.averageRating" :count="product.reviewCount" size="sm" />
        </div>
      </div>
    </button>
  </div>
</template>
