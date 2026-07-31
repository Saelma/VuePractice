<script setup>
/**
 * 관리자 쿠폰 관리 (2026-07-28). 그동안 쿠폰은 API만 있고 화면이 없어 curl 로만 만들 수 있었다.
 * 여기서 쿠폰을 만들고(정액/정률), 목록을 보고, 회원을 검색해 발급한다.
 *
 * 발급 대상 memberId 는 회원 검색(/api/admin/members)으로 찾는다 — 쿠폰 목록에 없는 건 회원 정보라
 * member 도메인 admin API 를 그대로 쓴다(도메인 경계).
 */
import { reactive, ref, computed, onMounted } from 'vue';
import { fetchAdminCoupons, createCoupon, issueCoupon, couponDiscountText, setWelcomeCoupon } from '../api/coupon';
import { fetchAdminMembers, roleText } from '../api/member';
import { priceText } from '../api/product';
import EmptyState from '../components/EmptyState.vue';

const coupons = ref([]);
const loading = ref(true);
const listError = ref('');

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleDateString('ko-KR') : '');

async function loadCoupons() {
  loading.value = true;
  try {
    coupons.value = (await fetchAdminCoupons({ size: 50 })).content;
  } catch (e) {
    listError.value = e.message;
  } finally {
    loading.value = false;
  }
}
onMounted(loadCoupons);

/*
 * 가입 쿠폰 지정(V36, 2026-07-31).
 *
 * ⚠ 예전엔 서버 설정(.env WELCOME_COUPON_ID)이라 **바꿀 때마다 재시작**이 필요했고, 무엇이 가입
 * 쿠폰인지 이 화면에서 보이지 않았다. 지금은 데이터라 여기서 바로 켜고 끈다.
 * 지정은 **한 장뿐** — 다른 걸 지정하면 이전 것은 서버가 자동으로 해제한다(그래서 목록을 다시 읽는다).
 */
const welcomeBusy = ref('');
async function toggleWelcome(c) {
  welcomeBusy.value = c.id;
  listError.value = '';
  try {
    await setWelcomeCoupon(c.id, !c.welcome);
    await loadCoupons(); // 이전 지정이 풀린 것까지 화면에 반영하려면 목록을 다시 받아야 한다
  } catch (e) {
    listError.value = e.message;
  } finally {
    welcomeBusy.value = '';
  }
}

// ---------- 생성 ----------
const form = reactive({
  name: '', discountType: 'FIXED', discountValue: null,
  minOrderAmount: 0, maxDiscountAmount: null, validFrom: '', validUntil: '',
});
const createMsg = reactive({ ok: '', err: '', loading: false });

const isPercent = computed(() => form.discountType === 'PERCENT');

function toIsoStart(d) { return d ? new Date(`${d}T00:00:00`).toISOString() : null; }
function toIsoEnd(d) { return d ? new Date(`${d}T23:59:59`).toISOString() : null; }

async function onCreate() {
  createMsg.ok = ''; createMsg.err = '';
  if (!form.name.trim()) { createMsg.err = '쿠폰명을 입력하세요.'; return; }
  if (!form.discountValue || form.discountValue <= 0) { createMsg.err = '할인값은 0보다 커야 합니다.'; return; }
  if (isPercent.value && form.discountValue > 100) { createMsg.err = '정률 할인은 100%를 넘을 수 없습니다.'; return; }
  if (!form.validFrom || !form.validUntil) { createMsg.err = '유효기간을 지정하세요.'; return; }
  if (form.validFrom > form.validUntil) { createMsg.err = '시작일이 종료일보다 늦습니다.'; return; }

  createMsg.loading = true;
  try {
    await createCoupon({
      name: form.name.trim(),
      discountType: form.discountType,
      discountValue: Number(form.discountValue),
      minOrderAmount: Number(form.minOrderAmount) || 0,
      maxDiscountAmount: isPercent.value && form.maxDiscountAmount ? Number(form.maxDiscountAmount) : null,
      validFrom: toIsoStart(form.validFrom),
      validUntil: toIsoEnd(form.validUntil),
    });
    createMsg.ok = `'${form.name.trim()}' 쿠폰을 만들었어요.`;
    Object.assign(form, { name: '', discountValue: null, minOrderAmount: 0, maxDiscountAmount: null, validFrom: '', validUntil: '' });
    await loadCoupons();
  } catch (e) {
    createMsg.err = e.message;
  } finally {
    createMsg.loading = false;
  }
}

// ---------- 발급 ----------
const selected = ref(null); // 발급할 쿠폰
const keyword = ref('');
const members = ref([]);
const issueMsg = reactive({ ok: '', err: '' });

function pickCoupon(c) {
  selected.value = c;
  members.value = [];
  keyword.value = '';
  issueMsg.ok = ''; issueMsg.err = '';
}

async function searchMembers() {
  issueMsg.err = '';
  try {
    members.value = (await fetchAdminMembers({ keyword: keyword.value.trim() || null, size: 8 })).content;
  } catch (e) {
    issueMsg.err = e.message;
  }
}

async function onIssue(member) {
  issueMsg.ok = ''; issueMsg.err = '';
  try {
    await issueCoupon(selected.value.id, member.id);
    issueMsg.ok = `${member.loginId}(${member.nickname}) 에게 '${selected.value.name}' 발급 완료.`;
  } catch (e) {
    issueMsg.err = e.message;
  }
}
</script>

<template>
  <section class="page">
    <h1 class="page-title mb-5">쿠폰 관리</h1>

    <div class="grid gap-6 lg:grid-cols-2">
      <!-- 쿠폰 생성 -->
      <form class="card flex flex-col gap-3 p-5" @submit.prevent="onCreate">
        <h2 class="section-title">쿠폰 생성</h2>
        <p v-if="createMsg.err" class="alert-error">{{ createMsg.err }}</p>
        <p v-if="createMsg.ok" class="alert-success">{{ createMsg.ok }}</p>

        <label class="field">
          <span class="field-label">쿠폰명</span>
          <input v-model="form.name" type="text" class="ipt" placeholder="가입 축하 5,000원" />
        </label>

        <div class="grid grid-cols-2 gap-3">
          <label class="field">
            <span class="field-label">할인 방식</span>
            <select v-model="form.discountType" class="ipt">
              <option value="FIXED">정액(원)</option>
              <option value="PERCENT">정률(%)</option>
            </select>
          </label>
          <label class="field">
            <span class="field-label">할인값 ({{ isPercent ? '%' : '원' }})</span>
            <input v-model.number="form.discountValue" type="number" min="1" class="ipt" />
          </label>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <label class="field">
            <span class="field-label">최소 주문금액 (0=제한없음)</span>
            <input v-model.number="form.minOrderAmount" type="number" min="0" class="ipt" />
          </label>
          <label v-if="isPercent" class="field">
            <span class="field-label">최대 할인액 (원, 선택)</span>
            <input v-model.number="form.maxDiscountAmount" type="number" min="0" class="ipt" />
          </label>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <label class="field">
            <span class="field-label">유효 시작일</span>
            <input v-model="form.validFrom" type="date" class="ipt" />
          </label>
          <label class="field">
            <span class="field-label">유효 종료일</span>
            <input v-model="form.validUntil" type="date" class="ipt" />
          </label>
        </div>

        <button type="submit" class="btn btn-primary self-start" :disabled="createMsg.loading">
          {{ createMsg.loading ? '생성 중…' : '쿠폰 생성' }}
        </button>
      </form>

      <!-- 회원 발급 -->
      <div class="card flex flex-col gap-3 p-5">
        <h2 class="section-title">회원 발급</h2>
        <p v-if="issueMsg.err" class="alert-error">{{ issueMsg.err }}</p>
        <p v-if="issueMsg.ok" class="alert-success">{{ issueMsg.ok }}</p>

        <p v-if="!selected" class="muted">아래 목록에서 발급할 쿠폰의 <b>발급</b> 버튼을 먼저 누르세요.</p>
        <template v-else>
          <p class="text-sm text-ink-700">
            발급할 쿠폰: <b class="text-ink-900">{{ selected.name }}</b> ({{ couponDiscountText(selected) }})
          </p>
          <div class="flex gap-2">
            <input v-model="keyword" type="text" class="ipt flex-1" placeholder="아이디·닉네임·이메일" @keyup.enter="searchMembers" />
            <button type="button" class="btn btn-secondary" @click="searchMembers">회원 검색</button>
          </div>
          <ul v-if="members.length" class="divide-y divide-line">
            <li v-for="m in members" :key="m.id" class="flex items-center justify-between gap-3 py-2">
              <div class="min-w-0">
                <p class="truncate text-sm text-ink-900">{{ m.loginId }} <span class="muted">· {{ m.nickname }}</span></p>
                <p class="muted">{{ roleText(m.role) }}</p>
              </div>
              <button type="button" class="btn btn-primary btn-sm" @click="onIssue(m)">발급</button>
            </li>
          </ul>
        </template>
      </div>
    </div>

    <!-- 쿠폰 목록 -->
    <div class="card mt-6 p-5">
      <h2 class="section-title mb-3">쿠폰 목록</h2>
      <p v-if="listError" class="alert-error">{{ listError }}</p>

      <div v-if="loading" class="space-y-2">
        <div v-for="n in 3" :key="n" class="skeleton h-14 w-full rounded-card"></div>
      </div>

      <ul v-else-if="coupons.length" class="divide-y divide-line">
        <li v-for="c in coupons" :key="c.id" class="flex flex-wrap items-center justify-between gap-3 py-3">
          <div class="min-w-0">
            <p class="text-sm font-medium text-ink-900">
              {{ c.name }}
              <!-- 무채색 원칙(DESIGN §2): 강조는 테두리·굵기로, 색은 쓰지 않는다. -->
              <span v-if="c.welcome" class="ml-1 rounded-full border border-ink-900 px-2 py-0.5 text-[11px] text-ink-900">
                가입 쿠폰
              </span>
            </p>
            <p class="muted tabular-nums">
              {{ couponDiscountText(c) }}
              · {{ c.minOrderAmount ? priceText(c.minOrderAmount) + ' 이상' : '금액 조건 없음' }}
              <span v-if="c.maxDiscountAmount">· 최대 {{ priceText(c.maxDiscountAmount) }}</span>
              · {{ fmtDate(c.validFrom) }}~{{ fmtDate(c.validUntil) }}
            </p>
          </div>
          <div class="flex shrink-0 items-center gap-2">
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              :class="c.welcome ? 'border-ink-900 text-ink-900' : ''"
              :disabled="welcomeBusy === c.id"
              @click="toggleWelcome(c)"
            >
              {{ c.welcome ? '가입 쿠폰 해제' : '가입 쿠폰으로' }}
            </button>
            <button type="button" class="btn btn-secondary btn-sm" :class="selected?.id === c.id ? 'border-brand-600 text-ink-900' : ''" @click="pickCoupon(c)">
              {{ selected?.id === c.id ? '선택됨' : '발급' }}
            </button>
          </div>
        </li>
      </ul>

      <EmptyState v-else icon="🎟️" message="아직 만든 쿠폰이 없어요. 위에서 만들어 보세요." />
    </div>
  </section>
</template>

<style scoped>
/* 네이티브 input/select 를 우리 토큰에 맞춘다(DxTextBox 없이 폼을 가볍게). */
.ipt {
  width: 100%;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-control);
  background: var(--color-surface);
  padding: 0.5rem 0.75rem;
  font-size: 0.875rem;
  color: var(--color-ink-900);
}
.ipt:focus-visible {
  outline: 2px solid var(--color-brand-600);
  outline-offset: 2px;
}
</style>
