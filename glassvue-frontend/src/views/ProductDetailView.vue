<script setup>
/**
 * 상품 상세 — 좌: 이미지 갤러리 / 우: 상품 정보의 2단 구성(DESIGN.md §7).
 * 정보를 한 줄에 늘어놓지 않고 카테고리 → 이름 → 가격 → 별점 → 재고 → 구매 순으로 읽히게 한다.
 */
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { getProduct, deleteProduct, statusText, priceText } from '../api/product';
import { addToCart } from '../api/cart';
import { authState, isLoggedIn } from '../stores/auth';
import StarRating from '../components/StarRating.vue';
import ProductReviews from '../components/ProductReviews.vue';
import ProductInquiries from '../components/ProductInquiries.vue';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const product = ref(null);
const error = ref('');
const loading = ref(true);
const isAdmin = computed(() => authState.user?.role === 'ADMIN');
const qty = ref(1);
const cartMsg = ref('');

/** 갤러리 표시용 로컬 상태 — 썸네일을 누르면 대표 이미지가 바뀐다(서버 데이터는 건드리지 않는다). */
const images = computed(() => product.value?.images ?? []);
const selected = ref(0);
const mainImage = computed(() => images.value[selected.value] ?? images.value[0] ?? null);

async function onAddToCart() {
  cartMsg.value = '';
  try {
    await addToCart(props.id, qty.value);
    cartMsg.value = '장바구니에 담았어요.';
  } catch (e) {
    error.value = e.message;
  }
}

onMounted(async () => {
  try {
    product.value = await getProduct(props.id);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
});

async function onDelete() {
  if (!window.confirm('이 상품을 삭제할까요?')) return;
  try {
    await deleteProduct(props.id);
    router.push('/products');
  } catch (e) {
    error.value = e.message;
  }
}
</script>

<template>
  <section class="page">
    <div v-if="error" class="alert-error mb-6">{{ error }}</div>

    <!-- 로딩: 텍스트 대신 스켈레톤으로 2단 레이아웃을 미리 잡는다 (DESIGN.md §5) -->
    <div v-if="loading" class="grid gap-8 lg:grid-cols-2">
      <div class="skeleton aspect-square w-full rounded-card"></div>
      <div class="space-y-4">
        <div class="skeleton h-3 w-20"></div>
        <div class="skeleton h-7 w-2/3"></div>
        <div class="skeleton h-9 w-40"></div>
        <div class="skeleton h-4 w-28"></div>
        <div class="skeleton h-24 w-full rounded-card"></div>
      </div>
    </div>

    <template v-else-if="product">
      <div class="grid gap-8 lg:grid-cols-2">
        <!-- 좌: 이미지 갤러리 -->
        <div>
          <div class="card aspect-square overflow-hidden bg-canvas">
            <img
              v-if="mainImage"
              :src="mainImage.mediumUrl"
              :alt="product.name"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full items-center justify-center text-5xl text-ink-400">🖼️</div>
          </div>

          <!-- 여러 장일 때만 썸네일 줄 -->
          <div v-if="images.length > 1" class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="(img, i) in images"
              :key="img.id"
              type="button"
              class="h-16 w-16 overflow-hidden rounded-control border bg-canvas transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
              :class="i === selected ? 'border-brand-600' : 'border-line hover:border-ink-400'"
              :aria-label="`${i + 1}번째 이미지 보기`"
              :aria-current="i === selected"
              @click="selected = i"
            >
              <img :src="img.thumbUrl" :alt="`${product.name} ${i + 1}`" class="h-full w-full object-cover" />
            </button>
          </div>
        </div>

        <!-- 우: 상품 정보 -->
        <div>
          <p class="muted">{{ product.categoryName }}</p>
          <h1 class="page-title mt-1">{{ product.name }}</h1>

          <p class="mt-4 text-3xl font-semibold tabular-nums text-ink-900">{{ priceText(product.price) }}</p>

          <div class="mt-3">
            <StarRating :model-value="product.averageRating" :count="product.reviewCount" />
          </div>

          <div class="mt-4 flex items-center gap-2">
            <span class="text-sm text-ink-700">재고 <b class="tabular-nums">{{ product.stock }}</b></span>
            <!-- 판매중이면 배지를 달지 않는다(노이즈 감소) -->
            <span
              v-if="product.status !== 'SELLING'"
              class="badge"
              :class="product.status === 'SOLD_OUT' ? 'badge-warning' : 'badge-neutral'"
            >{{ statusText(product.status) }}</span>
          </div>

          <!-- 구매 액션: 이 화면의 주 행동 -->
          <div v-if="isLoggedIn" class="card mt-6 flex flex-wrap items-end gap-3 p-5">
            <label class="field">
              <span class="field-label">수량</span>
              <DxNumberBox v-model:value="qty" :min="1" :width="90" />
            </label>
            <button type="button" class="btn btn-primary" @click="onAddToCart">장바구니 담기</button>
            <p v-if="cartMsg" class="alert-success w-full">{{ cartMsg }}</p>
          </div>

          <!-- 보조 행동 -->
          <div class="mt-6 flex gap-2">
            <button type="button" class="btn btn-secondary" @click="router.push('/products')">목록</button>
            <template v-if="isAdmin">
              <button type="button" class="btn btn-secondary" @click="router.push(`/products/${id}/edit`)">수정</button>
              <button type="button" class="btn btn-danger" @click="onDelete">삭제</button>
            </template>
          </div>
        </div>
      </div>

      <!-- 상품 설명 -->
      <section class="mt-10">
        <h2 class="section-title">상품 설명</h2>
        <p class="card-pad mt-3 min-h-[6rem] whitespace-pre-wrap text-sm text-ink-700">{{ product.description }}</p>
      </section>

      <div class="mt-10">
        <ProductReviews :product-id="id" />
      </div>
      <div class="mt-10">
        <ProductInquiries :product-id="id" />
      </div>
    </template>
  </section>
</template>
