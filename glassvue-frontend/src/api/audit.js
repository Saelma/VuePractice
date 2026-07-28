import { apiGet } from './client';

// --- 관리자 감사 이력 (SUPER_ADMIN 전용) ---
// 누가(actor) 누구를(target) 언제 어떻게 조작했는지의 append-only 이력. 조회 권한은 서버가
// /api/admin/audit/** = SUPER_ADMIN 으로 막는다(일반 ADMIN 은 403). 화면 진입도 라우터가 SUPER 만 통과.

/** 감사 이력 목록. action(조작 종류)·targetLogin(대상 loginId 부분일치)로 좁힐 수 있고, 정렬 미지정 시 최신순. */
export function fetchAuditLogs({ action = null, targetLogin = null, page = 0, size = 20 } = {}) {
  return apiGet('/api/admin/audit', { action, targetLogin, page, size });
}

export const AUDIT_ACTION_LABEL = {
  MEMBER_SUSPEND: '회원 정지',
  MEMBER_UNSUSPEND: '정지 해제',
  MEMBER_ROLE_CHANGE: '역할 변경',
};
export function auditActionText(action) {
  return AUDIT_ACTION_LABEL[action] || action || '';
}
