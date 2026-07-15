<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxTextArea } from 'devextreme-vue/text-area';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { DxButton } from 'devextreme-vue/button';
import { getProduct, createProduct, updateProduct, STATUS_OPTIONS } from '../api/product';
import { fetchCategories } from '../api/category';
import { uploadImage } from '../api/image';

const props = defineProps({ id: { type: String, default: null } });
const router = useRouter();
const isEdit = computed(() => !!props.id);

const categories = ref([]);
const form = reactive({ name: '', description: '', price: null, stock: null, status: 'SELLING', categoryId: null, images: [] });
const error = ref('');
const saving = ref(false);
const uploading = ref(false);

async function onFilesSelected(e) {
  const files = Array.from(e.target.files || []);
  if (!files.length) return;
  uploading.value = true;
  error.value = '';
  try {
    for (const file of files) {
      const img = await uploadImage(file); // { id, url }
      form.images.push(img);
    }
  } catch (err) {
    error.value = err.message;
  } finally {
    uploading.value = false;
    e.target.value = ''; // 같은 파일 재선택 허용
  }
}
function removeImage(idx) {
  form.images.splice(idx, 1);
}

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
  <section class="max-w-2xl p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">{{ isEdit ? '상품 수정' : '상품 등록' }}</h2>
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>

    <div class="flex flex-col gap-4 rounded-lg border bg-white p-6">
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">상품명</span>
        <DxTextBox v-model:value="form.name" />
      </label>
      <div class="flex gap-4">
        <label class="flex flex-1 flex-col gap-1">
          <span class="text-sm text-slate-600">카테고리</span>
          <DxSelectBox v-model:value="form.categoryId" :items="categories" value-expr="id" display-expr="name" placeholder="선택" />
        </label>
        <label class="flex flex-1 flex-col gap-1">
          <span class="text-sm text-slate-600">상태</span>
          <DxSelectBox v-model:value="form.status" :items="STATUS_OPTIONS" value-expr="value" display-expr="text" />
        </label>
      </div>
      <div class="flex gap-4">
        <label class="flex flex-1 flex-col gap-1">
          <span class="text-sm text-slate-600">가격(원)</span>
          <DxNumberBox v-model:value="form.price" :min="0" format="#,##0" />
        </label>
        <label class="flex flex-1 flex-col gap-1">
          <span class="text-sm text-slate-600">재고</span>
          <DxNumberBox v-model:value="form.stock" :min="0" format="#,##0" />
        </label>
      </div>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">설명</span>
        <DxTextArea v-model:value="form.description" :height="160" />
      </label>

      <div class="flex flex-col gap-2">
        <span class="text-sm text-slate-600">이미지</span>
        <input type="file" accept="image/*" multiple :disabled="uploading" @change="onFilesSelected" />
        <span v-if="uploading" class="text-sm text-slate-400">업로드 중…</span>
        <div v-if="form.images.length" class="flex flex-wrap gap-2">
          <div v-for="(img, idx) in form.images" :key="img.id" class="relative">
            <img :src="img.url" class="h-20 w-20 rounded border object-cover" />
            <button
              type="button"
              class="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-slate-700 text-xs text-white"
              @click="removeImage(idx)"
            >×</button>
          </div>
        </div>
      </div>

      <div class="mt-2 flex gap-2">
        <DxButton :text="saving ? '저장 중…' : '저장'" type="default" styling-mode="contained" :disabled="saving" @click="onSave" />
        <DxButton text="취소" styling-mode="outlined" @click="router.back()" />
      </div>
    </div>
  </section>
</template>
