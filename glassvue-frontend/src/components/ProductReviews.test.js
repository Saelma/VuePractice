import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// O-6 (2026-09-11) — 🔴 **관리자에게 모든 고객 리뷰의 「수정」이 보였다**(`isAdmin || mine` 하나로 수정·삭제를
// 같이 열었다). 서버도 받아 줬고 원장에도 안 남았다. 이제 수정은 **작성자만**, 삭제는 작성자·관리자이고
// 관리자가 남의 것을 지우면 **되돌릴 수 없고 감사에 남는다는 것을 누르기 전에** 말한다.
// ⚠ 이 화면에 테스트가 처음 생긴다 — `ProductInquiries.test.js` 와 같은 모양으로 둔다.

const fetchProductReviews = vi.fn();
const deleteReview = vi.fn();
vi.mock('../api/review', () => ({
  fetchProductReviews: (...a) => fetchProductReviews(...a),
  createReview: vi.fn(), updateReview: vi.fn(),
  deleteReview: (...a) => deleteReview(...a),
  REVIEW_IMAGE_MAX: 3,
  REVIEW_SORT_OPTIONS: [{ value: 'createdAt,desc', text: '최신순' }],
}));
vi.mock('../composables/useLoginRedirect', () => ({
  useLoginRedirect: () => ({ loginTo: () => '/login' }),
}));

import ProductReviews from './ProductReviews.vue';
import { setUser, clearSession } from '../stores/auth';

/** `ReviewResponse` 한 건 — ⚠ `authorId` 는 없다(R 축), 판정은 `mine` 이다. */
function review(overrides = {}) {
  return {
    id: 'r1', productId: 'p1', mine: false, author: '김**', rating: 4, content: '좋아요',
    images: [], createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z',
    ...overrides,
  };
}

let wrapper;
async function mountWith(rows) {
  fetchProductReviews.mockResolvedValue({
    averageRating: 4, reviewCount: rows.length,
    page: { content: rows, page: 0, totalPages: 1, last: true },
  });
  wrapper = mount(ProductReviews, {
    props: { productId: 'p1' },
    global: { stubs: { RouterLink: true, ImageUploader: true, DxTextArea: true, StarRating: true } },
  });
  await flushPromises();
  return wrapper;
}
const labels = (w) => w.findAll('button').map((b) => b.text());

describe('ProductReviews — 수정은 작성자만, 관리자는 삭제만 (O-6)', () => {
  beforeEach(() => { clearSession(); fetchProductReviews.mockReset(); deleteReview.mockReset(); });
  afterEach(() => { wrapper?.unmount(); clearSession(); vi.unstubAllGlobals(); });

  it('🔴 관리자는 남의 리뷰에 「삭제」만 본다 — 「수정」은 없다', async () => {
    setUser({ id: 'admin', role: 'ADMIN', nickname: '관리자' });
    const w = await mountWith([review({ mine: false })]);
    expect(labels(w)).toContain('삭제');
    expect(labels(w)).not.toContain('수정');
  });

  it('대조군 — 작성자는 자기 리뷰를 수정·삭제한다', async () => {
    setUser({ id: 'me', role: 'USER', nickname: '나' });
    const w = await mountWith([review({ mine: true })]);
    expect(labels(w)).toContain('수정');
    expect(labels(w)).toContain('삭제');
  });

  it('🔴 관리자가 남의 것을 지울 때는 «되돌릴 수 없고 감사에 남는다» 를 먼저 묻는다', async () => {
    const confirm = vi.fn(() => false);
    vi.stubGlobal('confirm', confirm);
    setUser({ id: 'admin', role: 'ADMIN', nickname: '관리자' });
    const w = await mountWith([review({ mine: false })]);

    await w.findAll('button').find((b) => b.text() === '삭제').trigger('click');

    expect(confirm.mock.calls[0][0]).toContain('감사 기록에 남습니다');
    expect(deleteReview).not.toHaveBeenCalled();   // 취소하면 안 지운다
  });

  it('대조군 — 자기 리뷰를 지울 때는 그 말이 없다(감사에 안 남는다)', async () => {
    const confirm = vi.fn(() => false);
    vi.stubGlobal('confirm', confirm);
    setUser({ id: 'me', role: 'USER', nickname: '나' });
    const w = await mountWith([review({ mine: true })]);

    await w.findAll('button').find((b) => b.text() === '삭제').trigger('click');

    expect(confirm.mock.calls[0][0]).not.toContain('감사');
  });
});
