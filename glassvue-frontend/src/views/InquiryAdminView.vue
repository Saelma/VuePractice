<script setup>
/**
 * 문의 관리 (관리자) — 2026-08-06, 백로그 G-3 1단계.
 *
 * 이 화면이 생기기 전까지 **관리자가 문의를 보는 길은 상품 상세의 문의 탭뿐**이었다(관리자 문의 API
 * 0개, 실측). 그래서 *"어느 상품이든 답을 기다리는 문의가 있나"* 를 볼 자리가 없었고, 상품과 무관한
 * 일반 문의는 **넣어도 답할 경로가 없었다** — G-3 이 여기에 막혀 있었다.
 *
 * ⚠ **기본이 「답변대기」다.** 관리자가 이 화면을 여는 이유가 *"답할 게 뭐가 남았나"* 라서다.
 *    「전체」도 고를 수 있다 — 서버는 기본값을 정하지 않고, 그 선택은 화면이 한다.
 *
 * 🔴 **비밀글도 본문이 그대로 보인다.** 관리자는 원래 열람 대상이고, 답을 쓰라면서 질문을 가릴 수는
 *    없다. 대신 **「비밀」 뱃지**로 표시한다 — 답변이 공개되지 않는다는 걸 알고 써야 하기 때문이다.
 *
 * ⚠ **답변은 기존 API(`answerInquiry`)를 그대로 쓴다.** 이 화면이 더한 것은 「무엇에 답할지 찾는
 *    길」 하나뿐이라, 답변 규칙(첫 답변에만 알림이 간다 등)은 상품 상세와 완전히 같다.
 */
import { ref } from 'vue';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { DxSelectBox } from 'devextreme-vue/select-box';
// ⚠ DxTextArea 는 **text-box 가 아니라 text-area** 모듈이다 — text-box 에서 가져오면 undefined 가
//    되어 입력칸이 통째로 안 그려지고, 빌드도 테스트도 통과한다(2026-08-03 에 실제로 겪었다).
import { DxTextArea } from 'devextreme-vue/text-area';
import {
  fetchAdminInquiries, answerInquiry, hideInquiry, unhideInquiry,
  INQUIRY_STATUS_OPTIONS, INQUIRY_HIDDEN_OPTIONS, inquiryTypeText,
} from '../api/inquiry';
import { DELETED_PRODUCT } from '../constants/labels';

// 기본 「답변대기」 — 목록을 여는 이유가 그것이다(위 주석).
//
// 🔴 숨김 기본은 **「보이는 것만」(false)** 이고, 관리자 리뷰 화면(기본 「전체」)과 **일부러 다르다**.
//    숨김과 답변 상태는 **독립**이라, 숨긴 미답변 문의가 「답변대기」 목록에 그대로 남는다 —
//    부적절해서 내린 문의를 매번 건너뛰며 «답할 것» 을 세게 된다.
//    리뷰 화면은 «숨길 것을 찾는» 용도라 전체가 맞고, 여기는 «답할 것을 찾는» 용도라 다르다.
// ⚠ 이건 **화면의 판단**이다 — 서버는 안 보내면 전체를 준다(`status` 기본값과 같은 규칙).
//    숨긴 것은 필터로 언제든 볼 수 있고, 목록에 뜰 때는 「숨김」 배지로 구분된다.
const filter = ref({ status: 'WAITING', hidden: false });
const gridRef = ref(null);
const error = ref('');

// 답변 중인 문의 한 건. 그리드 아래 카드로 펼친다(줄 안에서 쓰기엔 본문이 길다).
const editing = ref(null);
const answerText = ref('');
const saving = ref(false);

const store = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 20;
    const page = Math.floor((options.skip || 0) / size);
    try {
      // ⚠ status 는 null 을 그대로 넘긴다 — apiGet 이 null 만 뺀다.
      //    'ALL' 같은 문자열을 지어 보내면 서버가 400 을 내고, 화면엔 그게 "문의가 없다" 로 보인다.
      // ⚠ hidden 도 같다. 다만 여기는 **false 가 의미 있는 값**이라 falsy 처리를 하면 안 된다 —
      //    `hidden || undefined` 로 쓰면 「보이는 것만」이 조용히 「전체」가 된다.
      const res = await fetchAdminInquiries({
        status: filter.value.status, hidden: filter.value.hidden, page, size,
      });
      error.value = '';
      return { data: res.content, totalCount: res.totalElements };
    } catch (e) {
      // 실패를 빈 목록으로 위장하지 않는다 — 0건으로 그리면 "답할 문의가 없다"로 읽힌다(DESIGN §7).
      error.value = e.message;
      throw e;
    }
  },
});

function applyFilter() {
  editing.value = null;
  gridRef.value?.instance.refresh();
}

function startAnswer(row) {
  editing.value = row;
  answerText.value = row.answer || ''; // 이미 답한 문의는 고쳐 쓰는 것이라 원문을 채워 둔다
}

/**
 * 숨김 · 해제 (B-18 잔여, 2026-08-10).
 *
 * ⚠ **문구를 리뷰 화면에서 그대로 옮겨 오지 않았다.** 리뷰는 숨기면 평균 별점·리뷰 수까지
 *    움직이지만 문의는 집계가 없다 — 그대로 복사하면 **없는 결과를 예고하는 문구**가 된다.
 *    대신 문의에만 있는 것을 적는다: 「내 문의」에서도 빠지고, 작성자 본인에게도 안 보인다.
 * ⚠ 답변 카드를 열어 둔 채 숨기면 사라진 줄을 편집 중인 상태가 되므로 함께 닫는다.
 */
async function toggleHidden(row) {
  const willHide = !row.hidden;
  const message = willHide
    ? '이 문의를 숨길까요?\n\n상품 문의 목록과 「내 문의」에서 사라집니다.\n(작성자 본인에게도 보이지 않습니다.)\n\n관리자 목록에는 남고, 되돌릴 수 있습니다.'
    : '숨김을 해제할까요?\n\n다시 상품 문의 목록과 「내 문의」에 나타납니다.';
  if (!window.confirm(message)) return;

  error.value = '';
  try {
    await (willHide ? hideInquiry(row.id) : unhideInquiry(row.id));
    editing.value = null;
    gridRef.value?.instance.refresh();
  } catch (e) {
    error.value = e.message;
  }
}

function cancelAnswer() {
  editing.value = null;
  answerText.value = '';
}

async function saveAnswer() {
  const text = (answerText.value || '').trim();
  // 공백만 넣고 저장하면 서버가 400 을 낸다(@NotBlank). 여기서 먼저 막아 헛왕복을 줄인다.
  if (!text || !editing.value) return;

  saving.value = true;
  error.value = '';
  try {
    await answerInquiry(editing.value.id, text);
    editing.value = null;
    answerText.value = '';
    gridRef.value?.instance.refresh();
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}

/**
 * 상품명이 없는 줄 — 이제 **두 가지 뜻**이다(2026-08-07, 2단계가 들어온 뒤):
 * ① 유형이 PRODUCT 인데 이름이 없다 → 상품이 **지워진** 것이다(문의는 느슨한 참조라 함께 안 지워진다).
 * ② 유형이 PRODUCT 가 아니다 → 애초에 상품이 없는 **일반 문의**다.
 *
 * ⚠ 화면에서 둘이 구분되어야 한다 — 둘 다 «—» 로 그리면 관리자가 *"상품이 지워졌나?"* 를 매번
 *   의심하게 된다. 그래서 ②는 상품 칸을 비우고 **유형 열**이 답하게 한다.
 */
function productText(row) {
  if (row.type && row.type !== 'PRODUCT') return '—';
  return row.productName || DELETED_PRODUCT;
}
</script>

<template>
  <section class="page">
    <!-- 셸만 토큰/공용 클래스로. 표는 운영 화면이라 DataGrid 그대로 (DESIGN.md §7) -->
    <div class="mb-5">
      <h1 class="page-title">문의 관리</h1>
      <p class="muted mt-1">
        상품을 <strong>가로질러</strong> 문의를 봅니다. 기본은 <strong>답변대기</strong>이고,
        답변을 저장하면 작성자에게 <strong>알림이 갑니다</strong>(첫 답변에만).
        부적절한 문의는 <strong>숨김</strong> 처리합니다 — 삭제가 아니라 되돌릴 수 있고,
        숨겨도 <strong>이 목록에는 남습니다</strong>.
      </p>
    </div>

    <p v-if="error" class="alert-error mb-3">문의를 처리하지 못했습니다. {{ error }}</p>

    <div class="card mb-4 flex flex-wrap items-end gap-3 p-4">
      <label class="field">
        <span class="field-label">상태</span>
        <DxSelectBox
          v-model:value="filter.status"
          :items="INQUIRY_STATUS_OPTIONS"
          display-expr="text"
          value-expr="value"
          :width="160"
          @value-changed="applyFilter"
        />
      </label>
      <!--
        숨김 필터(B-18 잔여). 기본은 「보이는 것만」 — 근거는 스크립트의 filter 주석.
        ⚠ 이 칸이 없으면 숨긴 문의를 **되돌릴 방법이 없다**. 서버가 관리자 목록에만 숨긴 것을
          남겨 두는 이유가 그거라, 화면에 필터가 따라오지 않으면 그 설계가 무의미해진다.
      -->
      <label class="field">
        <span class="field-label">노출 상태</span>
        <DxSelectBox
          v-model:value="filter.hidden"
          :items="INQUIRY_HIDDEN_OPTIONS"
          display-expr="text"
          value-expr="value"
          :width="160"
          @value-changed="applyFilter"
        />
      </label>
    </div>

    <DxDataGrid
      ref="gridRef"
      :data-source="store"
      :remote-operations="true"
      :show-borders="false"
      :show-column-lines="false"
      :column-auto-width="true"
      :hover-state-enabled="true"
      :word-wrap-enabled="true"
      no-data-text="조건에 맞는 문의가 없습니다."
    >
      <DxColumn data-field="createdAt" caption="등록" :width="150" :calculate-display-value="(r) => fmt(r.createdAt)" />
      <!-- 유형(2026-08-07, 2단계) — 상품 칸이 비는 줄의 성격을 여기서 가린다.
           이게 없으면 관리자는 «상품 없는 문의» 가 배송 문제인지 환불 문제인지 열어 봐야 안다. -->
      <DxColumn data-field="type" caption="유형" :width="100" alignment="center"
                :calculate-display-value="(r) => inquiryTypeText(r.type)" />
      <DxColumn data-field="productName" caption="상품" :width="170" :calculate-display-value="productText" />
      <DxColumn data-field="author" caption="작성자" :width="110" />
      <DxColumn data-field="title" caption="제목" :width="200" cell-template="titleCell" />
      <DxColumn data-field="content" caption="내용" />
      <DxColumn data-field="status" caption="상태" :width="90" alignment="center" cell-template="stateCell" />
      <!-- 🔴 「상태」(답변)와 **별개 칸**이다 — 숨김과 답변 상태는 독립이라 한 칸에 겹쳐 놓으면
           «숨긴 미답변» 을 표시할 방법이 없다. 기본 필터가 「보이는 것만」이라 평소엔 전부 노출이지만,
           필터를 전체로 두면 여기서 갈린다. -->
      <DxColumn data-field="hidden" caption="노출" :width="80" alignment="center" cell-template="hiddenCell" />
      <DxColumn caption="처리" :width="150" alignment="center" cell-template="actionCell" />

      <DxPaging :page-size="20" />
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[20, 50]" :show-info="true" info-text="{2}건 중 {0}-{1}" />

      <template #titleCell="{ data }">
        <span>{{ data.data.title }}</span>
        <!-- 비밀글은 가리지 않되 **표시**한다 — 답변이 공개되지 않는다는 걸 알고 써야 한다 -->
        <span v-if="data.data.secret" class="badge badge-neutral ml-2">비밀</span>
      </template>
      <template #stateCell="{ data }">
        <span v-if="data.data.status === 'ANSWERED'" class="badge badge-success">답변완료</span>
        <span v-else class="badge badge-warning">답변대기</span>
      </template>
      <template #hiddenCell="{ data }">
        <span v-if="data.data.hidden" class="badge badge-danger">숨김</span>
        <span v-else class="badge badge-success">노출</span>
      </template>
      <template #actionCell="{ data }">
        <div class="flex justify-center gap-1">
          <!-- ⚠ 숨긴 문의에도 답변 버튼을 남긴다 — 숨김은 «공개를 내리는 것» 이고 답변은
               «작성자에게 답하는 것» 이라 별개다. 잘못 숨긴 것을 해제하기 전에 답할 수도 있다. -->
          <button type="button" class="btn btn-sm btn-secondary" @click="startAnswer(data.data)">
            {{ data.data.status === 'ANSWERED' ? '답변 수정' : '답변' }}
          </button>
          <button
            type="button"
            class="btn btn-sm"
            :class="data.data.hidden ? 'btn-secondary' : 'btn-danger'"
            @click="toggleHidden(data.data)"
          >{{ data.data.hidden ? '해제' : '숨김' }}</button>
        </div>
      </template>
    </DxDataGrid>

    <!-- 답변 작성 — 그리드 아래로 펼친다(본문이 길어 줄 안에서 쓰기 어렵다) -->
    <div v-if="editing" class="card mt-4 p-5">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <h2 class="section-title">답변 작성</h2>
        <span v-if="editing.secret" class="badge badge-neutral">비밀글 — 작성자만 봅니다</span>
      </div>

      <p class="muted text-sm">{{ productText(editing) }} · {{ editing.author }} · {{ fmt(editing.createdAt) }}</p>
      <p class="mt-2 font-medium">{{ editing.title }}</p>
      <p class="mt-1 whitespace-pre-wrap">{{ editing.content }}</p>

      <label class="field mt-4 block">
        <span class="field-label">답변</span>
        <DxTextArea v-model:value="answerText" :height="120" :max-length="2000"
                    placeholder="예: 내일 출고 예정입니다." />
      </label>

      <div class="mt-3 flex gap-2">
        <button type="button" class="btn btn-primary" :disabled="saving || !answerText.trim()" @click="saveAnswer">
          {{ saving ? '저장 중…' : '답변 저장' }}
        </button>
        <button type="button" class="btn btn-secondary" :disabled="saving" @click="cancelAnswer">취소</button>
      </div>
    </div>
  </section>
</template>
