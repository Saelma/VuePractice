<script setup>
/**
 * 찜 목록 (2026-07-24, 백로그 B-6).
 *
 * 상품 목록과 같은 카드 모양을 쓰되, 카드마다 **장바구니 담기**와 **찜 해제**를 붙인다 —
 * 찜의 목적은 "나중에 사는 것"이라 구매로 이어지는 경로가 화면 안에 있어야 한다.
 *
 * 가격·재고·별점은 찜한 시점이 아니라 **지금** 값이다(서버가 조회 때 합성한다).
 * 그래서 "담아둔 사이에 값이 내렸나 / 품절됐나"를 여기서 바로 알 수 있다.
 */
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { fetchWishlist } from '../api/wishlist';
import { removeWishlist } from '../api/wishlist';
import { addToCart } from '../api/cart';
import { loadCartCount } from '../stores/cart';
import { priceText, statusText, hasDiscount, discountRate } from '../api/product';
import { wishlistState } from '../stores/wishlist';
import StarRating from '../components/StarRating.vue';
import EmptyState from '../components/EmptyState.vue';
import ItemThumb from '../components/ItemThumb.vue';

const router = useRouter();
const items = ref([]);
const loading = ref(true);
const error = ref('');
const msg = ref('');
const busyId = ref(null);

async function load() {
  try {
    items.value = await fetchWishlist();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);

async function onAddToCart(item) {
  error.value = ''; msg.value = '';
  busyId.value = item.productId;
  try {
    await addToCart(item.productId, 1);
    msg.value = `'${item.name}'을(를) 장바구니에 담았어요.`;
    loadCartCount(true); // 헤더 🛒 배지 갱신
  } catch (e) {
    error.value = e.message;
  } finally {
    busyId.value = null;
  }
}

/**
 * 찜 해제 — 목록에서 바로 뺀다.
 * 여기서는 `toggleWishlist`를 쓰지 않고 직접 부른다. 이 화면의 행동은 토글이 아니라 **해제**뿐이고,
 * 목록에서도 함께 빼야 하기 때문이다. 다만 다른 화면의 하트가 낡지 않게 공유 집합도 갱신한다.
 */
async function onRemove(item) {
  error.value = ''; msg.value = '';
  busyId.value = item.productId;
  try {
    await removeWishlist(item.productId);
    items.value = items.value.filter((i) => i.productId !== item.productId);
    wishlistState.ids.delete(item.productId);
    msg.value = `'${item.name}'을(를) 찜에서 뺐어요.`;
  } catch (e) {
    error.value = e.message;
  } finally {
    busyId.value = null;
  }
}
</script>

<template>
  <section class="page">
    <div class="mb-5">
      <h1 class="page-title">찜 목록</h1>
      <p class="muted mt-1">
        가격과 재고는 <strong>지금</strong> 값이에요. 담아둔 사이에 값이 바뀌었을 수 있습니다.
      </p>
    </div>

    <p v-if="error" class="alert-error mb-5">{{ error }}</p>
    <p v-if="msg" class="alert-success mb-5">{{ msg }}</p>

    <div v-if="loading" class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
      <div v-for="n in 3" :key="n" class="card space-y-3 p-4">
        <div class="skeleton h-32 w-full"></div>
        <div class="skeleton h-4 w-2/3"></div>
        <div class="skeleton h-6 w-1/3"></div>
      </div>
    </div>

    <EmptyState
      v-else-if="!items.length"
      icon="♡"
      message="아직 찜한 상품이 없어요."
    >
      <button type="button" class="btn btn-primary" @click="router.push('/products')">상품 보러 가기</button>
    </EmptyState>

    <div v-else class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
      <div v-for="item in items" :key="item.productId" class="card flex flex-col p-4">
        <button
          type="button"
          class="flex gap-3 text-left"
          @click="router.push(`/products/${item.productId}`)"
        >
          <ItemThumb :src="item.thumbUrl" :alt="item.name" />
          <div class="min-w-0 flex-1">
            <h3 class="line-clamp-2 text-sm font-medium text-ink-900">{{ item.name }}</h3>
            <div class="mt-1">
              <span v-if="hasDiscount(item)" class="muted block tabular-nums line-through">
                {{ priceText(item.listPrice) }}
              </span>
              <span class="text-lg font-semibold tabular-nums text-ink-900">{{ priceText(item.price) }}</span>
              <span v-if="hasDiscount(item)" class="ml-1 text-sm font-semibold text-danger">
                {{ discountRate(item) }}%
              </span>
            </div>
            <StarRating :model-value="item.averageRating" :count="item.reviewCount" size="sm" class="mt-1" />
          </div>
        </button>

        <!-- 지금 살 수 없으면 이유를 말해 준다. 찜 자체는 유지된다 — 재입고를 기다리는 게 찜이다. -->
        <span v-if="!item.available" class="badge badge-warning mt-3 self-start">
          {{ statusText(item.status) }}
        </span>

        <div class="mt-3 flex gap-2 border-t border-line pt-3">
          <button
            type="button"
            class="btn btn-primary btn-sm flex-1"
            :disabled="!item.available || busyId === item.productId"
            @click="onAddToCart(item)"
          >
            장바구니 담기
          </button>
          <button
            type="button"
            class="btn btn-secondary btn-sm"
            :disabled="busyId === item.productId"
            @click="onRemove(item)"
          >
            찜 해제
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
