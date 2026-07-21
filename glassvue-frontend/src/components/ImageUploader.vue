<script setup>
/**
 * 이미지 업로드 + 미리보기 공용 컴포넌트.
 *
 * ProductFormView에 인라인으로만 있던 흐름을, 포토 리뷰가 두 번째 사용처가 되면서 뽑아냈다.
 * 업로드는 2단계다 — ①파일을 /api/images에 올려 {id,url}을 받고 ②저장 시 id 목록만 보낸다.
 * 백엔드의 image_group은 저장 시점에 생성되므로, 여기서는 그룹을 알 필요가 없다.
 *
 * v-model은 [{id, url}] 배열이다(id만이 아니라 url까지 들고 있어야 미리보기가 된다).
 */
import { ref, computed } from 'vue';
import { uploadImage } from '../api/image';

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  /** 최대 장수. 넘기면 초과 선택을 막는다. */
  max: { type: Number, default: null },
  /** 미리보기 한 변 크기(tailwind 클래스) */
  thumbClass: { type: String, default: 'h-20 w-20' },
});
const emit = defineEmits(['update:modelValue', 'error']);

const uploading = ref(false);
const inputRef = ref(null);

const isFull = computed(() => props.max !== null && props.modelValue.length >= props.max);

async function onFilesSelected(e) {
  const files = Array.from(e.target.files || []);
  if (!files.length) return;

  // 최대 장수 초과분은 잘라낸다 — 올린 뒤 거절하면 서버에 고아 이미지가 남는다.
  const room = props.max === null ? files.length : props.max - props.modelValue.length;
  if (room <= 0) {
    emit('error', `이미지는 최대 ${props.max}장까지 첨부할 수 있어요.`);
    e.target.value = '';
    return;
  }
  const targets = files.slice(0, room);
  if (targets.length < files.length) {
    emit('error', `이미지는 최대 ${props.max}장까지라 ${targets.length}장만 첨부했어요.`);
  }

  uploading.value = true;
  try {
    const added = [];
    for (const file of targets) {
      added.push(await uploadImage(file)); // { id, url }
    }
    emit('update:modelValue', [...props.modelValue, ...added]);
  } catch (err) {
    emit('error', err.message);
  } finally {
    uploading.value = false;
    e.target.value = ''; // 같은 파일 재선택 허용
  }
}

function removeAt(idx) {
  const next = [...props.modelValue];
  next.splice(idx, 1);
  emit('update:modelValue', next);
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <input
      ref="inputRef"
      type="file"
      accept="image/*"
      multiple
      :disabled="uploading || isFull"
      @change="onFilesSelected"
    />
    <span v-if="uploading" class="text-sm text-ink-500">업로드 중…</span>
    <span v-else-if="isFull" class="text-sm text-ink-500">최대 {{ max }}장까지 첨부했어요.</span>

    <div v-if="modelValue.length" class="flex flex-wrap gap-2">
      <div v-for="(img, idx) in modelValue" :key="img.id" class="relative">
        <img :src="img.thumbUrl" class="rounded border object-cover" :class="thumbClass" />
        <button
          type="button"
          class="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand-600 text-xs text-white"
          :aria-label="`${idx + 1}번째 이미지 삭제`"
          @click="removeAt(idx)"
        >×</button>
      </div>
    </div>
  </div>
</template>
