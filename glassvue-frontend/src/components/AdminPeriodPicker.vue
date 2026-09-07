<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { PRESETS, presetRange, matchedPreset, fetchServerToday } from '../api/period';

/**
 * 관리자 **기간 선택** 공용 컨트롤 (B-26 · DESIGN §7 — 네이티브 date 2칸 + 프리셋).
 *
 * ⚠ **DevExtreme 을 쓰지 않는다.** 쿠폰은 네이티브, 공지는 `DxDateBox` 로 이미 둘로 갈려 있었고
 * DESIGN §7 이 «관리자 기간 선택은 네이티브» 로 못박았다. DX 컨트롤을 넣으면 그 테마가 따라 들어온다.
 *
 * 🔴 **매출 화면(StatsAdminView)은 아직 자기 markup 을 쓴다.** 2026-09-07 에 이 컴포넌트를 뽑으면서
 * 거기까지 바꾸지 않았다 — **그 화면에는 뷰 테스트가 없어서** 바꾼 것이 맞는지 판정할 수단이 없다.
 * ⚠ 사본이 둘이라는 것을 알고 두는 것이고, 핸드오프 이월에 적어 뒀다.
 */
const props = defineProps({
  /** 'yyyy-MM-dd' 또는 빈 문자열 */
  from: { type: String, default: '' },
  to: { type: String, default: '' },
});
const emit = defineEmits(['change']);

const localFrom = ref(props.from);
const localTo = ref(props.to);
/** 서버가 알려 준 KST 오늘. 못 받으면 프리셋 버튼을 잠근다(브라우저 시계로 대신하지 않는다). */
const todayKst = ref('');

watch(() => props.from, (v) => { localFrom.value = v; });
watch(() => props.to, (v) => { localTo.value = v; });

const activePreset = computed(() => matchedPreset(localFrom.value, localTo.value, todayKst.value));

onMounted(async () => {
  try {
    const res = await fetchServerToday();
    todayKst.value = res.today;
  } catch {
    // 🔴 기준을 못 받으면 프리셋만 잠근다 — 직접 고르기는 계속 된다.
    //    ⚠ 여기서 new Date() 로 넘어가면 「이번 달」이 조용히 다른 뜻이 된다.
    todayKst.value = '';
  }
});

function applyPreset(key) {
  const range = presetRange(key, todayKst.value);
  localFrom.value = range.from;
  localTo.value = range.to;
  emit('change', { from: range.from, to: range.to });
}

/** 직접 고른 경우 — ⚠ **한쪽만 고른 상태로는 안 보낸다**(반쪽 기간은 질문이 안 된다). */
function applyManual() {
  if (localFrom.value && localTo.value) {
    emit('change', { from: localFrom.value, to: localTo.value });
  }
}

function clear() {
  localFrom.value = '';
  localTo.value = '';
  emit('change', { from: '', to: '' });
}
</script>

<template>
  <div class="flex flex-wrap items-end gap-x-4 gap-y-3">
    <label class="field">
      <span class="field-label">시작일</span>
      <input v-model="localFrom" type="date" class="ipt" :max="localTo || undefined" @change="applyManual" />
    </label>
    <label class="field">
      <span class="field-label">종료일 (포함)</span>
      <input v-model="localTo" type="date" class="ipt" :min="localFrom || undefined" @change="applyManual" />
    </label>
    <div class="flex flex-wrap gap-2">
      <button
        v-for="p in PRESETS"
        :key="p.key"
        type="button"
        class="btn btn-secondary btn-sm"
        :class="activePreset === p.key ? 'border-ink-900 text-ink-900' : ''"
        :disabled="!todayKst"
        :title="todayKst ? undefined : '서버 기준 날짜를 못 받아 프리셋을 쓸 수 없어요'"
        @click="applyPreset(p.key)"
      >{{ p.label }}</button>
      <button
        v-if="localFrom || localTo"
        type="button"
        class="btn btn-ghost btn-sm"
        @click="clear"
      >기간 해제</button>
    </div>
  </div>
</template>
