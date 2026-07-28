<script setup>
/**
 * 상품 카드 — 목록·홈에서 함께 쓴다(중복 방지). 원래 ProductListView 안에 있던 마크업을 뽑아 온 것이라
 * 두 화면의 카드가 갈리지 않는다(홈 재구성 B-8 에서 추출).
 *
 * 카드가 통째로 <button>이라 찜 하트를 그 안에 넣을 수 없다(button 중첩) → 래퍼 div에 절대배치한다.
 */
import { useRouter } from 'vue-router';
import { priceText, hasDiscount, discountRate, statusText } from '../api/product';
import StarRating from './StarRating.vue';
import WishlistButton from './WishlistButton.vue';

const props = defineProps({
  product: { type: Object, required: true },
});

const router = useRouter();
const thumbOf = (p) => (p.images && p.images.length ? p.images[0].thumbUrl : null);
</script>

<template>
  <div class="relative">
    <WishlistButton :product-id="product.id" class="absolute right-3 top-3 z-10" />
    <button
      type="button"
      class="group w-full overflow-hidden rounded-card bg-surface text-left shadow-card transition duration-200 hover:-translate-y-0.5 hover:shadow-lift focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
      @click="router.push(`/products/${product.id}`)"
    >
      <div class="relative aspect-square overflow-hidden bg-canvas">
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
      </div>

      <div class="p-4">
        <p class="text-xs text-ink-500">{{ product.categoryName }}</p>
        <h3 class="mt-0.5 line-clamp-1 text-sm font-medium text-ink-900">{{ product.name }}</h3>
        <div class="mt-2 flex items-end justify-between gap-2">
          <div class="min-w-0">
            <!-- 할인율은 이미지 뱃지로 옮겼다. 여기선 정가 취소선(위) + 판매가(아래)만. 정가 없으면 판매가만. -->
            <span v-if="hasDiscount(product)" class="muted block tabular-nums line-through">{{ priceText(product.listPrice) }}</span>
            <span class="text-lg font-semibold tabular-nums text-ink-900">{{ priceText(product.price) }}</span>
          </div>
          <StarRating :model-value="product.averageRating" :count="product.reviewCount" size="sm" />
        </div>
        <span
          v-if="product.status !== 'SELLING'"
          class="badge mt-2"
          :class="product.status === 'SOLD_OUT' ? 'badge-warning' : 'badge-neutral'"
        >{{ statusText(product.status) }}</span>
        <!-- 판매중이지만 전 옵션이 품절인 경우도 품절로 표시(soldOut은 서버가 계산) -->
        <span v-else-if="product.soldOut" class="badge badge-warning mt-2">품절</span>
      </div>
    </button>
  </div>
</template>
