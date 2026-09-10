import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// R 축(2026-09-10) — 「내 글인가」의 **판정 자리를 서버로 옮긴 것**을 고정한다.
//
// 🔴 전에는 응답의 `authorId` 를 화면이 직접 비교했다(`q.authorId === myId`). 그 한 줄 때문에
//    **비로그인이 부르는 공개 목록**이 남의 회원 UUID 를 전부 싣고 나갔다. PK 가 UUIDv7 이라
//    그 값은 **가입 시각**까지 들고 나간다 — 실측으로 회원 세 명의 가입 시각을 복원했다.
//    이제 서버가 `mine` 한 칸으로 답하고, 화면은 그것만 본다.
//
// ⚠ 그래서 이 테스트가 지키는 것은 버튼이 아니라 **「화면이 남의 id 를 필요로 하지 않는다」** 는
//    사실이다. 아래 픽스처에는 `authorId` 가 **아예 없다** — 그게 있어야 초록이면 회귀다.

const fetchProductInquiries = vi.fn();
vi.mock('../api/inquiry', () => ({
  fetchProductInquiries: (...a) => fetchProductInquiries(...a),
  createInquiry: vi.fn(), updateInquiry: vi.fn(), deleteInquiry: vi.fn(), answerInquiry: vi.fn(),
  inquiryStatusText: (s) => s,
  INQUIRY_IMAGE_MAX: 3,
}));
vi.mock('../composables/useLoginRedirect', () => ({
  useLoginRedirect: () => ({ loginTo: () => '/login' }),
}));

import ProductInquiries from './ProductInquiries.vue';
import { setUser, clearSession } from '../stores/auth';

/** 서버 응답 한 건. ⚠ `authorId` 는 **없다** — 이제 안 내려간다. */
function inquiry(overrides = {}) {
  return {
    id: 'q1', productId: 'p1', mine: false, author: '김기현팀',
    title: '문의합니다', content: '내용', secret: false, status: 'WAITING',
    answer: null, answeredAt: null, images: [], masked: false,
    createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z',
    ...overrides,
  };
}

async function mountWith(rows) {
  fetchProductInquiries.mockResolvedValueOnce(
      { content: rows, page: 0, totalPages: 1, last: true });
  const w = mount(ProductInquiries, {
    props: { productId: 'p1' },
    global: { stubs: { RouterLink: true, ImageUploader: true, DxTextArea: true, DxTextBox: true, DxCheckBox: true } },
  });
  await flushPromises();
  return w;
}

const labels = (w) => w.findAll('button').map((b) => b.text());

describe('ProductInquiries — 「내 글인가」는 서버의 mine 으로만 판정한다', () => {
  beforeEach(() => { clearSession(); fetchProductInquiries.mockReset(); });
  afterEach(() => clearSession());

  it('mine=true 이고 답변 전이면 수정·삭제가 보인다', async () => {
    setUser({ id: 'me', role: 'USER', nickname: '나' });
    const w = await mountWith([inquiry({ mine: true, status: 'WAITING' })]);
    expect(labels(w)).toContain('수정');
    expect(labels(w)).toContain('삭제');
  });

  it('대조군 — 같은 글이라도 mine=false 면 수정·삭제가 사라진다', async () => {
    setUser({ id: 'me', role: 'USER', nickname: '나' });
    const w = await mountWith([inquiry({ mine: false, status: 'WAITING' })]);
    expect(labels(w)).not.toContain('수정');
    expect(labels(w)).not.toContain('삭제');
  });

  it('답변이 달리면(ANSWERED) 본인이어도 수정은 못 한다 — 삭제만 남는다', async () => {
    setUser({ id: 'me', role: 'USER', nickname: '나' });
    const w = await mountWith([inquiry({ mine: true, status: 'ANSWERED' })]);
    expect(labels(w)).not.toContain('수정');
    expect(labels(w)).toContain('삭제');
  });

  it('관리자는 남의 글에 「답변하기」가 보이고, 자기 글에는 안 보인다', async () => {
    setUser({ id: 'admin', role: 'ADMIN', nickname: '관리자' });
    const other = await mountWith([inquiry({ mine: false })]);
    expect(labels(other)).toContain('답변하기');

    clearSession();
    setUser({ id: 'admin', role: 'ADMIN', nickname: '관리자' });
    const own = await mountWith([inquiry({ mine: true })]);
    expect(labels(own)).not.toContain('답변하기');
  });

  it('비로그인은 아무 관리 버튼도 못 본다', async () => {
    const w = await mountWith([inquiry({ mine: false })]);
    expect(labels(w)).not.toContain('수정');
    expect(labels(w)).not.toContain('삭제');
    expect(labels(w)).not.toContain('답변하기');
  });

  it('비밀글은 제목까지 서버가 가려 보낸다 — 화면은 받은 것을 그대로 쓴다', async () => {
    const w = await mountWith([inquiry({ secret: true, masked: true, title: '🔒 비밀글입니다.', content: '🔒 비밀글입니다.' })]);
    expect(w.text()).toContain('🔒 비밀글입니다.');
    expect(w.text()).not.toContain('문의합니다');
  });
});
