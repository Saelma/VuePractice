<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxButton } from 'devextreme-vue/button';
import { getProduct, deleteProduct, statusText, priceText } from '../api/product';
import { authState } from '../stores/auth';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const product = ref(null);
const error = ref('');
const loading = ref(true);
const isAdmin = computed(() => authState.user?.role === 'ADMIN');

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

    <article v-else-if="product" class="rounded-lg border bg-white p-6">
      <div class="mb-2 flex items-center gap-2">
        <h2 class="text-2xl font-bold text-slate-800">{{ product.name }}</h2>
        <span class="rounded bg-slate-100 px-2 py-0.5 text-sm text-slate-600">{{ statusText(product.status) }}</span>
      </div>
      <div class="mb-4 flex flex-wrap gap-4 border-b pb-3 text-sm text-slate-500">
        <span>카테고리 <b class="text-slate-700">{{ product.categoryName }}</b></span>
        <span class="text-lg font-semibold text-slate-800">{{ priceText(product.price) }}</span>
        <span>재고 {{ product.stock }}</span>
      </div>
      <p class="min-h-[6rem] whitespace-pre-wrap text-slate-700">{{ product.description }}</p>

      <div class="mt-6 flex gap-2">
        <DxButton text="목록" styling-mode="outlined" @click="router.push('/products')" />
        <template v-if="isAdmin">
          <DxButton text="수정" type="default" styling-mode="contained" @click="router.push(`/products/${id}/edit`)" />
          <DxButton text="삭제" type="danger" styling-mode="contained" @click="onDelete" />
        </template>
      </div>
    </article>
  </section>
</template>
