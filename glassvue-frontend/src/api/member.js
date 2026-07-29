import { apiGet, apiPost, apiPatch, apiDelete } from './client';
import { setUser, clearSession } from '../stores/auth';

// --- 관리자 회원 조회 (B-11) ---
// 회원 목록/검색·기본상세만 여기. 그 회원의 주문은 api/order, 적립금은 api/point 의 admin 함수로 붙인다
// (백엔드도 도메인별로 소유 — member 는 order/point 를 참조하지 않는다).

/** 회원 목록·검색(loginId·nickname·email 부분일치). 정렬 미지정 시 최신 가입 순. */
export function fetchAdminMembers({ keyword = null, page = 0, size = 10 } = {}) {
  return apiGet('/api/admin/members', { keyword, page, size });
}

/** 회원 기본상세. */
export function fetchAdminMember(memberId) {
  return apiGet(`/api/admin/members/${memberId}`);
}

export const ROLE_LABEL = { USER: '일반', ADMIN: '관리자', SUPER_ADMIN: '최상위 관리자' };
export function roleText(role) {
  return ROLE_LABEL[role] || role || '';
}

// --- 관리자 회원 조작 (B-11 후속) — 정지/해제·역할변경. 응답은 갱신된 회원(AdminMemberResponse). ---
// ⚠ 자기 계정은 서버가 400(MEMBER-400S)으로 막는다(락아웃 방지). 화면도 자기 행은 버튼을 숨긴다.
export function suspendMember(memberId) {
  return apiPost(`/api/admin/members/${memberId}/suspend`);
}
export function unsuspendMember(memberId) {
  return apiPost(`/api/admin/members/${memberId}/unsuspend`);
}
export function changeMemberRole(memberId, role) {
  return apiPatch(`/api/admin/members/${memberId}/role`, { role });
}

export async function changeNickname(nickname) {
  const me = await apiPatch('/api/members/me/nickname', { nickname });
  setUser(me); // 헤더 닉네임 즉시 갱신 (토큰 claim은 다음 로그인 때 갱신)
  return me;
}

/**
 * 이메일 등록·변경 (B-13). 응답이 갱신된 회원이라 스토어도 같이 갱신한다
 * — 설정 화면이 다시 그려질 때 초기값이 옛 값으로 되돌아가지 않게.
 * ⚠ 서버가 소문자로 정규화해 저장하므로, 화면은 **입력값이 아니라 응답값**을 표시해야 한다.
 */
export async function changeEmail(email) {
  const me = await apiPatch('/api/members/me/email', { email });
  setUser(me);
  return me;
}

/** 기본 배송지 저장. 응답이 갱신된 회원이라 스토어도 같이 갱신해 주문서 자동 채움에 바로 반영된다. */
export async function updateShippingAddress(address) {
  const me = await apiPatch('/api/members/me/shipping-address', address);
  setUser(me);
  return me;
}

export function changePassword(currentPassword, newPassword) {
  return apiPatch('/api/members/me/password', { currentPassword, newPassword });
}

export async function withdraw() {
  await apiDelete('/api/members/me');
  clearSession();
}
