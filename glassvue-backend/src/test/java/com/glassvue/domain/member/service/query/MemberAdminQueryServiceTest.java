package com.glassvue.domain.member.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class MemberAdminQueryServiceTest {

    @Mock MemberRepository memberRepository;
    @InjectMocks MemberAdminQueryService service;

    private Member member() {
        return Member.builder().loginId("kim").password("HASH").nickname("김철수").role(Role.USER).build();
    }

    @Test
    @DisplayName("검색: 결과를 AdminMemberResponse 페이지로 변환")
    void search_maps() {
        when(memberRepository.searchForAdmin(any(), any()))
                .thenReturn(new PageImpl<>(List.of(member())));
        PageResponse<?> res = service.search("kim", PageRequest.of(0, 10));
        assertThat(res.content()).hasSize(1);
    }

    @Test
    @DisplayName("검색: 빈/공백 keyword 는 null 로 넘겨 전체 조회")
    void search_blankKeywordBecomesNull() {
        when(memberRepository.searchForAdmin(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search("   ", PageRequest.of(0, 10));
        verify(memberRepository).searchForAdmin(isNull(), any());
    }

    @Test
    @DisplayName("검색: 정렬 미지정이면 createdAt DESC 기본 적용")
    void search_defaultSort() {
        when(memberRepository.searchForAdmin(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search(null, PageRequest.of(0, 10)); // sort 없음
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).searchForAdmin(isNull(), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("createdAt"))
                .isNotNull()
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("상세: 존재하면 기본정보 반환")
    void get_found() {
        UUID id = UUID.randomUUID();
        when(memberRepository.findById(id)).thenReturn(Optional.of(member()));
        assertThat(service.get(id).loginId()).isEqualTo("kim");
    }

    @Test
    @DisplayName("상세: 없으면 MEMBER_NOT_FOUND")
    void get_notFound() {
        UUID id = UUID.randomUUID();
        when(memberRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
