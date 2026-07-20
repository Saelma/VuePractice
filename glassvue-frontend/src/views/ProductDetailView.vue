<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxButton } from 'devextreme-vue/button';
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
  <section class="max-w-2xl p-6">
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>
    <div v-else-if="loading" class="text-slate-500">불러오는 중…</div>

    <template v-else-if="product">
    <article class="rounded-lg border bg-white p-6">
      <div class="mb-2 flex items-center gap-2">
        <h2 class="text-2xl font-bold text-slate-800">{{ product.name }}</h2>
        <span class="rounded bg-slate-100 px-2 py-0.5 text-sm text-slate-600">{{ statusText(product.status) }}</span>
      </div>
      <div class="mb-4 flex flex-wrap items-center gap-4 border-b pb-3 text-sm text-slate-500">
        <span>카테고리 <b class="text-slate-700">{{ product.categoryName }}</b></span>
        <span class="text-lg font-semibold text-slate-800">{{ priceText(product.price) }}</span>
        <span>재고 {{ product.stock }}</span>
        <StarRating :model-value="product.averageRating" :count="product.reviewCount" />
      </div>
      <div v-if="product.images && product.images.length" class="mb-4 flex flex-wrap gap-2">
        <img v-for="img in product.images" :key="img.id" :src="img.url" class="h-32 w-32 rounded border object-cover" />
      </div>

      <p class="min-h-[6rem] whitespace-pre-wrap text-slate-700">{{ product.description }}</p>

      <div v-if="isLoggedIn" class="mt-4 flex items-center gap-2">
        <DxNumberBox v-model:value="qty" :min="1" :width="90" />
        <DxButton text="장바구니 담기" type="success" styling-mode="contained" @click="onAddToCart" />
        <span v-if="cartMsg" class="text-sm text-green-600">{{ cartMsg }}</span>
      </div>

      <div class="mt-6 flex gap-2">
        <DxButton text="목록" styling-mode="outlined" @click="router.push('/products')" />
        <template v-if="isAdmin">
          <DxButton text="수정" type="default" styling-mode="contained" @click="router.push(`/products/${id}/edit`)" />
          <DxButton text="삭제" type="danger" styling-mode="contained" @click="onDelete" />
        </template>
      </div>
    </article>

    <ProductReviews :product-id="id" />
    <ProductInquiries :product-id="id" />
    </template>
  </section>
</template>
