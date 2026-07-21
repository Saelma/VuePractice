import { describe, it, expect } from 'vitest';
import { addressFromUser, hasAddress, validateAddress, trimAddress, addressText } from './shipping';

const ok = { recipient: '홍길동', phone: '010-1234-5678', zipcode: '06134', address1: '서울시 강남구 1', address2: '3층' };

describe('validateAddress', () => {
  it('필수 4개가 차 있으면 통과 (상세 주소는 선택)', () => {
    expect(validateAddress(ok)).toBe('');
    expect(validateAddress({ ...ok, address2: '' })).toBe('');
  });

  it('공백만 있는 값은 미입력으로 본다 — DxTextBox에서 스페이스만 넣고 넘어가는 걸 막는다', () => {
    expect(validateAddress({ ...ok, recipient: '   ' })).toContain('수령인');
  });

  it('필드별로 메시지가 다르다', () => {
    expect(validateAddress({ ...ok, zipcode: '' })).toContain('우편번호');
    expect(validateAddress({ ...ok, address1: '' })).toContain('주소');
  });

  it('백엔드 @Size와 같은 상한을 건다 (서버 400 대신 화면에서 먼저 걸린다)', () => {
    expect(validateAddress({ ...ok, recipient: 'ㄱ'.repeat(51) })).toContain('50자');
    expect(validateAddress({ ...ok, address2: 'ㄱ'.repeat(201) })).toContain('상세 주소');
  });
});

describe('trimAddress', () => {
  it('앞뒤 공백을 털고, 빈 상세 주소는 null로 보낸다', () => {
    const r = trimAddress({ ...ok, recipient: ' 홍길동 ', address2: '  ' });
    expect(r.recipient).toBe('홍길동');
    expect(r.address2).toBeNull();
  });
});

describe('addressFromUser / hasAddress', () => {
  it('기본 배송지 미설정(회원 ship* 없음)이면 빈 폼', () => {
    expect(addressFromUser(null)).toEqual({ recipient: '', phone: '', zipcode: '', address1: '', address2: '' });
    expect(hasAddress({ nickname: 'x' })).toBe(false);
  });

  it('회원의 ship* 를 폼 키로 옮긴다', () => {
    const user = { shipRecipient: '홍길동', shipPhone: '010', shipZipcode: '06134', shipAddress1: '서울', shipAddress2: null };
    expect(addressFromUser(user)).toEqual({ recipient: '홍길동', phone: '010', zipcode: '06134', address1: '서울', address2: '' });
    expect(hasAddress(user)).toBe(true);
  });
});

describe('addressText', () => {
  it('배송지 도입(V11) 이전 주문은 빈 문자열 — 화면에서 감출 수 있게', () => {
    expect(addressText({ shipAddress1: null })).toBe('');
  });

  it('상세 주소가 없어도 공백이 겹치지 않는다', () => {
    expect(addressText({ shipZipcode: '06134', shipAddress1: '서울시 강남구 1', shipAddress2: null }))
      .toBe('(06134) 서울시 강남구 1');
  });
});
