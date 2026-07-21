/**
 * 배송지 공통 규칙 — 주문서(/checkout)와 마이페이지가 같은 필드·같은 검증을 쓴다.
 * 화면마다 검증을 따로 두면 한쪽만 통과하고 서버에서 400을 받는다.
 * 길이 제한은 백엔드 DTO(@Size)·컬럼 길이와 맞춘 값이다.
 */

export function emptyAddress() {
  return { recipient: '', phone: '', zipcode: '', address1: '', address2: '' };
}

/** 회원 정보(MemberResponse.ship*)를 폼 모양으로. 기본 배송지 미설정이면 빈 값. */
export function addressFromUser(user) {
  return {
    recipient: user?.shipRecipient || '',
    phone: user?.shipPhone || '',
    zipcode: user?.shipZipcode || '',
    address1: user?.shipAddress1 || '',
    address2: user?.shipAddress2 || '',
  };
}

export function hasAddress(user) {
  return !!(user?.shipRecipient && user?.shipAddress1);
}

const RULES = [
  ['recipient', '수령인', 50],
  ['phone', '연락처', 20],
  ['zipcode', '우편번호', 10],
  ['address1', '주소', 200],
];

/** 통과하면 '' , 아니면 사용자에게 보여줄 메시지 하나. (상세주소는 선택) */
export function validateAddress(a) {
  for (const [key, label, max] of RULES) {
    const v = (a[key] || '').trim();
    if (!v) return `${label}을(를) 입력하세요.`;
    if (v.length > max) return `${label}은(는) ${max}자 이하여야 합니다.`;
  }
  if ((a.address2 || '').trim().length > 200) return '상세 주소는 200자 이하여야 합니다.';
  return '';
}

/** 저장 전 정리 — 공백만 있는 상세주소는 null로 보내 빈 문자열이 쌓이지 않게 한다. */
export function trimAddress(a) {
  const t = (v) => (v || '').trim();
  return {
    recipient: t(a.recipient),
    phone: t(a.phone),
    zipcode: t(a.zipcode),
    address1: t(a.address1),
    address2: t(a.address2) || null,
  };
}

/** 한 줄 표기 — 주문 상세·주문서 요약에서 쓴다. 배송지 도입(V11) 이전 주문은 값이 없다. */
export function addressText(o) {
  if (!o?.shipAddress1) return '';
  return [`(${o.shipZipcode})`, o.shipAddress1, o.shipAddress2].filter(Boolean).join(' ');
}
