<script setup>
/**
 * 고객센터 — 2026-08-07, 백로그 G-3 2·3단계.
 *
 * 🔴 **여기가 없으면 「상품과 무관한 문의」를 할 데가 없다.** 문의 작성 경로가
 * `POST /products/{id}/inquiries` 하나뿐이라, *"배송이 안 와요"* 를 물으려면 **아무 상품이나
 * 골라야** 했고 그 문의는 그 상품의 문의 목록에 남의 일처럼 걸렸다.
 *
 * 🔴 **「내 문의」가 같은 화면에 있는 것이 핵심이다** — 편의가 아니라 **주소**다.
 * 답변 알림(B-15)의 링크는 원래 `/products/{productId}#inquiries` 하나뿐인데, 일반 문의는
 * 상품이 없어 **착지할 URL 자체가 없다.** 그래서 알림은 `/support#inquiry-{id}` 로 오고,
 * 이 화면이 그 줄까지 데려간다.
 *
 * ⚠ **상품 문의도 함께 보인다.** 사용자는 «내가 물어본 것» 이 상품에 달렸는지 고객센터에 냈는지
 *    기억하지 못한다 — 가르면 두 목록을 번갈아 봐야 한다. 대신 줄마다 **유형 배지**로 구분한다.
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
// ⚠ DxTextArea 는 **text-area** 모듈이다 — text-box 에서 가져오면 undefined 가 되어 입력칸이
//    통째로 안 그려지고, 빌드도 테스트도 통과한다(2026-08-03 에 겪었다).
import { DxTextArea } from 'devextreme-vue/text-area';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { DxCheckBox } from 'devextreme-vue/check-box';
import {
  createGeneralInquiry, fetchMyInquiries, inquiryStatusText, inquiryTypeText,
  GENERAL_INQUIRY_TYPE_OPTIONS, INQUIRY_IMAGE_MAX,
} from '../api/inquiry';
import ImageUploader from '../components/ImageUploader.vue';
import EmptyState from '../components/EmptyState.vue';
import SkeletonList from '../components/SkeletonList.vue';
import { useAnchorScroll } from '../composables/useAnchorScroll';

const route = useRoute();
const { scrollToAnchor, cancel } = useAnchorScroll();

const page = ref({ content: [], page: 0, totalPages: 0, totalElements: 0, last: true });
const loading = ref(true);
const error = ref('');

// 기본 유형을 「배송」으로 둔 이유: 고객센터로 오는 문의에서 가장 흔하다. 고르지 않아도
// 유효한 값이 들어가지만, 선택지가 셋뿐이라 사용자가 바꾸는 비용도 낮다.
const form = ref({ type: 'DELIVERY', title: '', content: '', secret: true, images: [] });
const submitting = ref(false);
const formError = ref('');
const submitted = ref('');

// 알림에서 들어온 줄 — 잠깐 강조해 «이 줄이다» 를 보여 준다.
const highlightId = ref(null);
const rowRefs = new Map();

function setRowRef(id, el) {
  if (el) rowRefs.set(id, el);
  else rowRefs.delete(id); // 목록이 다시 그려지면 옛 요소를 남기지 않는다
}

const isEmpty = computed(() => !loading.value && !page.value.content.length);

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    page.value = await fetchMyInquiries({ page: p, size: 10 });
  } catch (e) {
    // 실패를 빈 목록으로 위장하지 않는다 — 0건으로 그리면 "문의한 적 없다"로 읽힌다(DESIGN §7).
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function submit() {
  formError.value = '';
  submitted.value = '';
  if (!form.value.title.trim() || !form.value.content.trim()) {
    formError.value = '제목과 내용을 입력하세요.';
    return;
  }
  submitting.value = true;
  try {
    await createGeneralInquiry({
      type: form.value.type,
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      secret: form.value.secret,
      imageIds: form.value.images.map((i) => i.id),
    });
    form.value = { type: 'DELIVERY', title: '', content: '', secret: true, images: [] };
    submitted.value = '문의를 접수했어요. 답변이 등록되면 알림으로 알려드릴게요.';
    await load(0); // 방금 쓴 것이 아래 목록에 바로 보여야 «접수됐다» 가 믿긴다
  } catch (e) {
    formError.value = e.message;
  } finally {
    submitting.value = false;
  }
}

function fmt(iso) {
  return iso ? new Date(iso).toLocaleDateString('ko-KR') : '';
}

/**
 * 상품명 칸 — 유형과 **함께** 읽어야 뜻이 정해진다.
 * 일반 문의는 애초에 상품이 없고, 상품 문의인데 이름이 없으면 **상품이 지워진** 것이다
 * (문의는 느슨한 참조라 함께 안 지워진다). 둘 다 «내가 물어본 것» 이라 줄은 남긴다.
 */
function productText(q) {
  if (q.type !== 'PRODUCT') return null;
  return q.productName || '(지워진 상품)';
}

onMounted(async () => {
  await load(0);

  // 🔴 알림에서 `/support#inquiry-{id}` 로 들어오면 그 줄까지 데려간다.
  //
  // ⚠ **라우터 scrollBehavior 로 하지 않는다** — 그 시점엔 목록을 아직 못 받아 줄이 렌더되기
  //    전이라 앵커 요소가 없다. 데이터가 온 **뒤**인 여기가 맞는 자리다(상품 상세와 같은 판단).
  // ⚠ 그리고 `loading` 을 내린 **뒤 nextTick** 이라야 한다 — 본문이 v-if 뒤에 있어 스켈레톤
  //    상태에서는 ref 가 전부 null 이다(WA §2-8 이 실제 사고로 적어 둔 자리).
  const m = /^#inquiry-(.+)$/.exec(route.hash || '');
  if (!m) return;
  const id = m[1];
  await nextTick();
  highlightId.value = id;
  scrollToAnchor(rowRefs.get(id) || null);
});

onBeforeUnmount(cancel); // 2초 안에 다른 화면으로 가면 관측·타이머가 남지 않게
</script>

<template>
  <section class="page">
    <div class="mb-5">
      <h1 class="page-title">고객센터</h1>
      <p class="muted mt-1">
        주문·배송·환불 등 <strong>상품과 무관한 문의</strong>를 남기는 곳입니다.
        특정 상품에 대한 질문은 그 상품 페이지의 <strong>문의</strong> 탭이 더 빠릅니다.
      </p>
    </div>

    <!-- ── 문의하기 ─────────────────────────────────────────── -->
    <div class="card mb-8 flex flex-col gap-4 p-5">
      <h2 class="section-title">문의하기</h2>

      <label class="field">
        <span class="field-label">유형</span>
        <DxSelectBox
          v-model:value="form.type"
          :items="GENERAL_INQUIRY_TYPE_OPTIONS"
          display-expr="text"
          value-expr="value"
          :width="180"
        />
      </label>

      <label class="field">
        <span class="field-label">제목</span>
        <DxTextBox v-model:value="form.title" :max-length="200" placeholder="예: 주문한 상품이 아직 안 왔어요" />
      </label>

      <label class="field">
        <span class="field-label">내용</span>
        <DxTextArea
          v-model:value="form.content"
          :height="110"
          :max-length="2000"
          placeholder="주문번호를 함께 적어주시면 더 빨리 확인할 수 있어요."
        />
      </label>

      <div class="field">
        <span class="field-label">사진 첨부 (최대 {{ INQUIRY_IMAGE_MAX }}장)</span>
        <ImageUploader v-model="form.images" :max="INQUIRY_IMAGE_MAX" thumb-class="h-16 w-16" @error="formError = $event" />
      </div>

      <p v-if="formError" class="field-error">{{ formError }}</p>
      <p v-if="submitted" class="alert-success">{{ submitted }}</p>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <!-- 기본이 **비밀글**이다 — 고객센터 문의엔 주문번호·연락처가 들어가기 쉽다.
             상품 문의(공개 기본)와 반대인 이유가 그것이다. -->
        <DxCheckBox v-model:value="form.secret" text="비밀글 (나와 상담원만 열람)" />
        <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '접수 중…' : '문의 등록' }}
        </button>
      </div>
    </div>

    <!-- ── 내 문의 ──────────────────────────────────────────── -->
    <header class="mb-4 flex flex-wrap items-baseline justify-between gap-2">
      <h2 class="section-title">내 문의</h2>
      <span v-if="page.totalElements" class="muted tabular-nums">{{ page.totalElements }}건</span>
    </header>

    <p v-if="error" class="alert-error mb-3">문의를 불러오지 못했습니다. {{ error }}</p>

    <SkeletonList v-if="loading" />

    <EmptyState
      v-else-if="isEmpty"
      density="section"
      icon="💬"
      message="아직 남긴 문의가 없어요."
      hint="위에서 문의를 남기면 여기에 답변이 함께 쌓입니다."
    />

    <ul v-else class="card divide-y divide-line">
      <li
        v-for="q in page.content"
        :key="q.id"
        :id="`inquiry-${q.id}`"
        :ref="(el) => setRowRef(q.id, el)"
        class="scroll-mt-28 px-5 py-4 transition-colors"
        :class="{ 'bg-canvas': highlightId === q.id }"
      >
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <span class="badge badge-neutral">{{ inquiryTypeText(q.type) }}</span>
          <span v-if="q.secret" title="비밀글">🔒</span>
          <span class="min-w-0 text-sm font-medium text-ink-900">{{ q.title }}</span>
          <span class="badge" :class="q.status === 'ANSWERED' ? 'badge-success' : 'badge-warning'">
            {{ inquiryStatusText(q.status) }}
          </span>
          <span class="muted ml-auto shrink-0">{{ fmt(q.createdAt) }}</span>
        </div>

        <!-- 상품 문의 줄에만 상품이 붙는다 — 그 상품 페이지로 돌아갈 수 있어야 한다 -->
        <p v-if="productText(q)" class="muted mb-2 text-xs">
          <RouterLink v-if="q.productId" :to="`/products/${q.productId}#inquiries`" class="hover:text-ink-900">
            {{ productText(q) }}
          </RouterLink>
          <span v-else>{{ productText(q) }}</span>
        </p>

        <p class="whitespace-pre-wrap text-sm text-ink-700">{{ q.content }}</p>

        <div v-if="q.images?.length" class="mt-3 flex flex-wrap gap-2">
          <a v-for="img in q.images" :key="img.id" :href="img.url" target="_blank" rel="noopener">
            <img :src="img.thumbUrl" alt="문의 이미지" class="h-16 w-16 rounded-control border border-line object-cover" />
          </a>
        </div>

        <div v-if="q.answer" class="mt-3 rounded-control border-l-4 border-success bg-canvas p-3">
          <div class="mb-1 text-xs font-semibold text-success">답변 · {{ fmt(q.answeredAt) }}</div>
          <p class="whitespace-pre-wrap text-sm text-ink-700">{{ q.answer }}</p>
        </div>
      </li>
    </ul>

    <div v-if="page.totalPages > 1" class="mt-6 flex items-center justify-center gap-4">
      <button type="button" class="btn btn-secondary" :disabled="page.page === 0" @click="load(page.page - 1)">이전</button>
      <span class="text-sm tabular-nums text-ink-500">{{ page.page + 1 }} / {{ page.totalPages }}</span>
      <button type="button" class="btn btn-secondary" :disabled="page.last" @click="load(page.page + 1)">다음</button>
    </div>
  </section>
</template>
