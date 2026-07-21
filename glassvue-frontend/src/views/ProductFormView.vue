<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxTextArea } from 'devextreme-vue/text-area';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { getProduct, createProduct, updateProduct, STATUS_OPTIONS } from '../api/product';
import { fetchCategories } from '../api/category';
import ImageUploader from '../components/ImageUploader.vue';

const props = defineProps({ id: { type: String, default: null } });
const router = useRouter();
const isEdit = computed(() => !!props.id);

const categories = ref([]);
const form = reactive({ name: '', description: '', price: null, stock: null, status: 'SELLING', categoryId: null, images: [] });
const error = ref('');
const saving = ref(false);

onMounted(async () => {
  try {
    categories.value = await fetchCategories();
  } catch (e) {
    /* ignore */
  }
  if (isEdit.value) {
    try {
      const p = await getProduct(props.id);
      Object.assign(form, {
        name: p.name, description: p.description, price: p.price,
        stock: p.stock, status: p.status, categoryId: p.categoryId,
      });
      form.images = p.images || [];
    } catch (e) {
      error.value = e.message;
    }
  }
});

async function onSave() {
  error.value = '';
  if (!form.name.trim() || !form.description.trim()) { error.value = '상품명·설명은 필수입니다.'; return; }
  if (form.price == null || form.stock == null) { error.value = '가격·재고를 입력하세요.'; return; }
  if (!form.categoryId) { error.value = '카테고리를 선택하세요.'; return; }
  saving.value = true;
  try {
    const payload = {
      name: form.name, description: form.description, price: form.price,
      stock: form.stock, status: form.status, categoryId: form.categoryId,
      imageIds: form.images.map((i) => i.id),
    };
    if (isEdit.value) {
      await updateProduct(props.id, payload);
      router.push(`/products/${props.id}`);
    } else {
      const id = await createProduct(payload);
      router.push(`/products/${id}`);
    }
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="page-narrow">
    <h1 class="page-title mb-5">{{ isEdit ? '상품 수정' : '상품 등록' }}</h1>

    <!-- 전역/서버 에러는 상단 박스, 필드 단위 검증은 필드 아래(DESIGN.md §5) -->
    <div v-if="error" class="alert-error mb-4">{{ error }}</div>

    <div class="card flex flex-col gap-4 p-5">
      <label class="field">
        <span class="field-label">상품명</span>
        <DxTextBox v-model:value="form.name" />
      </label>
      <div class="flex gap-4">
        <label class="field flex-1">
          <span class="field-label">카테고리</span>
          <DxSelectBox v-model:value="form.categoryId" :items="categories" value-expr="id" display-expr="name" placeholder="선택" />
        </label>
        <label class="field flex-1">
          <span class="field-label">상태</span>
          <DxSelectBox v-model:value="form.status" :items="STATUS_OPTIONS" value-expr="value" display-expr="text" />
        </label>
      </div>
      <div class="flex gap-4">
        <label class="field flex-1">
          <span class="field-label">가격(원)</span>
          <DxNumberBox v-model:value="form.price" :min="0" format="#,##0" />
        </label>
        <label class="field flex-1">
          <span class="field-label">재고</span>
          <DxNumberBox v-model:value="form.stock" :min="0" format="#,##0" />
        </label>
      </div>
      <label class="field">
        <span class="field-label">설명</span>
        <DxTextArea v-model:value="form.description" :height="160" />
      </label>

      <div class="field">
        <span class="field-label">이미지</span>
        <ImageUploader v-model="form.images" @error="error = $event" />
      </div>

      <div class="mt-2 flex gap-2">
        <button type="button" class="btn btn-primary" :disabled="saving" @click="onSave">
          {{ saving ? '저장 중…' : '저장' }}
        </button>
        <button type="button" class="btn btn-secondary" @click="router.back()">취소</button>
      </div>
    </div>
  </section>
</template>
