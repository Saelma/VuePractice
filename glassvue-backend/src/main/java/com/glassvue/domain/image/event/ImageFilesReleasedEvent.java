package com.glassvue.domain.image.event;

import com.glassvue.global.messaging.DomainEvent;
import java.util.List;

/**
 * 이미지 row가 삭제되어 **실제 파일도 지워야 할 때** 발행되는 이벤트.
 *
 * <p>파일 삭제를 트랜잭션 안에서 하지 않는 이유: 롤백되면 DB row는 살아나는데 파일은 이미
 * 사라져 **깨진 이미지**가 된다. 파일 삭제는 되돌릴 수 없으므로 커밋이 확정된 뒤에만 한다
 * ({@code AFTER_COMMIT}). 반대로 파일 삭제가 실패해도 DB는 이미 정리됐고, 남은 파일은
 * 다음 정리 주기에 다시 대상이 되지 않으므로(row가 없음) 디스크에만 남는다 — 무해한 방향의 실패다.
 *
 * @param urls 삭제 대상 파일 url 목록({@code store()}가 돌려준 형태)
 */
public record ImageFilesReleasedEvent(List<String> urls) implements DomainEvent {
}
