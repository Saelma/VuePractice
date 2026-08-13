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
  // 비우면 상시 쿠폰. 넣으면 그 날까지만 「받기」로 발급되는 **이벤트 쿠폰**이다(G-8, V49).
  issueUntil: '',
});
const createMsg = reactive({ ok: '', err: '', loading: false });

const isPercent = computed(() => form.discountType === 'PERCENT');

function toIsoStart(d) { return d ? new Date(`${d}T00:00:00`).toISOString() : null; }
function toIsoEnd(d) { return d ? new Date(`${d}T23:59:59`).toISOString() : null; }

/**
 * 이벤트 쿠폰의 **사용 기한 한 달**은 코드가 아니라 **기본값**에 있다 (2026-08-13, G-8).
 *
 * ⚠ 정책을 서버에 박으면 이벤트마다 다르게 갈 수 없고 컬럼도 는다. `validUntil` 은 이미 관리자가
 * 입력하는 칸이니 **화면이 채워 주고, 다른 이벤트는 관리자가 고친다.**
 * ⚠ 이미 입력해 둔 값은 **덮어쓰지 않는다** — 관리자가 정한 것을 화면이 되돌리면 안 된다.
 */
function onIssueUntilChange() {
  if (!form.issueUntil || form.validUntil) return;
  form.validUntil = plusOneMonth(form.validFrom || form.issueUntil);
}

function plusOneMonth(day) {
  const base = new Date(`${day}T00:00:00`);
  base.setMonth(base.getMonth() + 1);
  // ⚠ toISOString() 은 UTC 라 KST 오전에 하루 앞선 날짜가 나온다 — 로컬 날짜를 직접 만든다.
  return `${base.getFullYear()}-${String(base.getMonth() + 1).padStart(2, '0')}-${String(base.getDate()).padStart(2, '0')}`;
}

/**
 * 🔴 **지금 무엇을 만들고 있는지 화면이 말한다** (2026-08-13, 검증에서 드러난 자리).
 *
 * ⚠ **사고**: 발급 마감일을 필수 항목 아래 선택 칸으로만 둬서, 지나쳐도 아무 신호가 없었다.
 * 첫 검증에서 이벤트 쿠폰을 만들려던 두 건이 **상시 쿠폰으로 조용히 만들어졌고**, 그러면
 * 배지도 안 뜨고 겹침 검사도 안 돌고 배너도 안 나온다 — **전부 「설계대로」인데 전부 틀려 보인다.**
 * → 폼이 **자기가 만들 것을 문장으로** 되읽어 준다. 값이 아니라 **결과**를 보여주는 것이 요점이다.
 */
/**
 * 이미 잡혀 있는 발급 창 — **겹칠 대상을 폼에서 미리 보여준다**.
 *
 * ⚠ 목록에는 상시 쿠폰이 섞여 있어 눈으로 맞추기 어렵다. «겹친다» 는 거부를 받고 나서
 * *"겹치는 게 없는데?"* 가 됐던 자리다(2026-08-13). 서버 에러도 무엇과 겹치는지 말하지만,
 * **누르기 전에 아는 편**이 낫다.
 */
const eventWindows = computed(() =>
  coupons.value
    .filter((c) => c.issueUntil)
    .map((c) => `${c.name} (${fmtDate(c.validFrom)}~${fmtDate(c.issueUntil)})`));

const plan = computed(() => {
  if (!form.issueUntil) {
    return { event: false, text: '상시 쿠폰 — 관리자가 직접 발급합니다. 홈 배너에는 안 뜹니다.' };
  }
  if (!form.validFrom || !form.validUntil) {
    return { event: true, text: '이벤트 쿠폰 — 유효 시작일·종료일을 마저 넣어 주세요.' };
  }
  const sameDay = form.issueUntil === form.validUntil;
  return {
    event: true,
    sameDay,
    text: `이벤트 쿠폰 — ${form.validFrom} ~ ${form.issueUntil} 동안 홈 배너의 「받기」로 발급되고, `
        + `받은 사람은 ${form.validUntil}까지 씁니다.`,
  };
});

async function onCreate() {
  createMsg.ok = ''; createMsg.err = '';
  if (!form.name.trim()) { createMsg.err = '쿠폰명을 입력하세요.'; return; }
  if (!form.discountValue || form.discountValue <= 0) { createMsg.err = '할인값은 0보다 커야 합니다.'; return; }
  if (isPercent.value && form.discountValue > 100) { createMsg.err = '정률 할인은 100%를 넘을 수 없습니다.'; return; }
  if (!form.validFrom || !form.validUntil) { createMsg.err = '유효기간을 지정하세요.'; return; }
  if (form.validFrom > form.validUntil) { createMsg.err = '시작일이 종료일보다 늦습니다.'; return; }
  // ⚠ 서버도 같은 것을 막는다(COUPON-400W). 여기서 먼저 보는 건 왕복 없이 알려주기 위해서다 —
  //    「겹치는 이벤트가 이미 있다」는 화면이 알 수 없어 서버 답을 그대로 띄운다.
  if (form.issueUntil) {
    if (form.issueUntil < form.validFrom) { createMsg.err = '발급 마감일이 시작일보다 빠릅니다.'; return; }
    if (form.issueUntil > form.validUntil) { createMsg.err = '발급 마감일이 사용 종료일보다 늦습니다 — 받자마자 만료됩니다.'; return; }
  }

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
      issueUntil: toIsoEnd(form.issueUntil), // 비었으면 null → 상시 쿠폰
    });
    createMsg.ok = form.issueUntil
      ? `'${form.name.trim()}' 이벤트 쿠폰을 만들었어요. ${form.validFrom}에 홈 배너가 열립니다.`
      : `'${form.name.trim()}' 쿠폰을 만들었어요.`;
    Object.assign(form, {
      name: '', discountValue: null, minOrderAmount: 0, maxDiscountAmount: null,
      validFrom: '', validUntil: '', issueUntil: '',
    });
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
    <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
      <h1 class="page-title">쿠폰 관리</h1>
      <!-- 달력은 조회 도구라 관리자 메뉴가 아니라 여기서 들어간다(B-27) -->
      <RouterLink to="/admin/coupons/calendar" class="btn btn-secondary btn-sm">프로모션 달력</RouterLink>
    </div>

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

        <!--
          이벤트 쿠폰 (G-8). 🔴 **발급 창과 사용 기간은 다른 것이다** — 여기를 헷갈리면
          "그 날 하루" 쿠폰이 그 날 자정에 만료돼 받자마자 못 쓴다.
          비워 두면 지금까지와 같은 상시 쿠폰이라, 이 칸이 곧 기능의 on/off 다("지정 안 함 = 꺼짐").
          ⚠ 그래서 **테두리로 묶어** 선택 칸이 아니라 갈림길로 보이게 한다 — 그냥 한 줄로 두었더니
          지나쳐도 아무 신호가 없어 상시 쿠폰이 조용히 만들어졌다(2026-08-13 검증).
        -->
        <fieldset class="rounded-card border border-line p-4">
          <legend class="px-1 text-xs text-ink-500">이벤트 쿠폰으로 만들기 (선택)</legend>
          <label class="field">
            <span class="field-label">발급 마감일 — 비우면 상시 쿠폰</span>
            <input v-model="form.issueUntil" type="date" class="ipt" @change="onIssueUntilChange" />
          </label>
          <!--
            ⚠ 한 문단으로 붙여 놨더니 줄이 안 나뉘어 안 읽혔다(2026-08-13, 사용자 지적).
            규칙이 넷이라 **넷으로 끊는다** — 설명은 문장 수가 아니라 항목 수대로 나눈다.
          -->
          <ul class="mt-2 flex flex-col gap-1 text-xs text-ink-500">
            <li>· 유효 시작일부터 이 날까지 <b>홈 배너의 「받기」</b>로만 발급됩니다.</li>
            <li>· <b>회원당 한 장</b>입니다.</li>
            <li>· <b>발급 창이 겹치는 이벤트</b>는 등록되지 않습니다(사용 기간은 겹쳐도 됩니다).</li>
            <li>· 사용 종료일이 비어 있으면 <b>한 달 뒤</b>로 채워 드립니다.</li>
          </ul>

          <!-- 이미 잡힌 발급 창 — 없으면 줄을 안 만든다(빈 「없음」은 자리만 먹는다) -->
          <p v-if="eventWindows.length" class="mt-2 text-xs text-ink-700">
            이미 잡힌 발급 창: <b>{{ eventWindows.join(' · ') }}</b>
          </p>

          <!-- 값이 아니라 **결과**를 되읽어 준다 — 무엇을 만들고 있는지 누르기 전에 알게 한다. -->
          <p class="mt-3 text-sm" :class="plan.event ? 'text-ink-900' : 'text-ink-500'">
            {{ plan.text }}
          </p>
          <!--
            ⚠ 막지는 않는다 — 「그 날 하루만 쓰는 쿠폰」이 의도일 수도 있다.
            다만 이게 이 기능이 애초에 막으려던 모양이라 **말은 해 준다.**
          -->
          <p v-if="plan.sameDay" class="mt-1 text-sm text-warning">
            ⚠ 발급 마감과 사용 종료가 같은 날입니다 — 받은 사람은 <b>그 날 자정까지만</b> 쓸 수 있어요.
          </p>
        </fieldset>

        <!-- 버튼도 무엇을 만드는지 말한다 — 마지막 순간까지 되돌릴 기회를 준다 -->
        <button type="submit" class="btn btn-primary self-start" :disabled="createMsg.loading">
          {{ createMsg.loading ? '생성 중…' : (plan.event ? '이벤트 쿠폰 생성' : '쿠폰 생성') }}
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
              <span v-if="c.issueUntil" class="ml-1 rounded-full border border-ink-900 px-2 py-0.5 text-[11px] text-ink-900">
                이벤트
              </span>
            </p>
            <p class="muted tabular-nums">
              {{ couponDiscountText(c) }}
              · {{ c.minOrderAmount ? priceText(c.minOrderAmount) + ' 이상' : '금액 조건 없음' }}
              <span v-if="c.maxDiscountAmount">· 최대 {{ priceText(c.maxDiscountAmount) }}</span>
              · 사용 {{ fmtDate(c.validFrom) }}~{{ fmtDate(c.validUntil) }}
              <!-- 발급 창을 따로 적는다 — 사용 기간과 같은 줄에 섞으면 둘이 같은 값처럼 읽힌다. -->
              <span v-if="c.issueUntil">· 발급 {{ fmtDate(c.validFrom) }}~{{ fmtDate(c.issueUntil) }}</span>
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
