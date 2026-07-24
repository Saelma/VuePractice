import { describe, it, expect } from 'vitest';
import { addressToForm, addressSummary } from './address';

const item = {
  id: 'a1', alias: '집', recipient: '홍길동', phone: '010-1234-5678',
  zipcode: '06134', address1: '서울시 강남구 테헤란로 1', address2: '3층', isDefault: true,
};

describe('addressToForm', () => {
  it('주소록 항목을 배송지 폼 모양으로 바꾼다 (별칭·기본여부는 폼에 없다)', () => {
    expect(addressToForm(item)).toEqual({
      recipient: '홍길동', phone: '010-1234-5678', zipcode: '06134',
      address1: '서울시 강남구 테헤란로 1', address2: '3층',
    });
  });

  it('상세 주소가 null이어도 폼은 빈 문자열이어야 한다 — DxTextBox에 null을 넣으면 안 된다', () => {
    expect(addressToForm({ ...item, address2: null }).address2).toBe('');
  });

  it('항목이 없으면 빈 폼 (주소록 조회 실패 시에도 화면이 깨지지 않는다)', () => {
    expect(addressToForm(null)).toEqual({
      recipient: '', phone: '', zipcode: '', address1: '', address2: '',
    });
  });
});

describe('addressSummary', () => {
  it('우편번호 + 주소 + 상세주소를 한 줄로', () => {
    expect(addressSummary(item)).toBe('(06134) 서울시 강남구 테헤란로 1 3층');
  });

  it('상세 주소가 없으면 그 자리를 비우지 않고 아예 뺀다 (공백 두 칸 방지)', () => {
    expect(addressSummary({ ...item, address2: null })).toBe('(06134) 서울시 강남구 테헤란로 1');
  });

  it('항목이 없으면 빈 문자열', () => {
    expect(addressSummary(null)).toBe('');
  });
});
