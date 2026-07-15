package kr.co.ecstel.esp.global.common;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 엔티티의 공통 상위 클래스.
 * - PK: UUIDv7(시간순)을 앱에서 생성, Oracle에는 RAW(16) 바이너리로 저장.
 * - 감사: Spring Data JPA Auditing으로 createdAt/updatedAt 자동 관리(Instant, UTC).
 * (로그인 도입 시 이 클래스를 상속한 BaseEntity에 작성자 @CreatedBy/@LastModifiedBy를 얹는다)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "RAW(16)", updatable = false, nullable = false)
    private UUID id = UuidCreator.getTimeOrderedEpoch();

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Override
    public UUID getId() {
        return id;
    }

    /**
     * Spring Data가 신규(persist) vs 기존(merge)을 판단하는 기준.
     * id를 앱에서 미리 채우므로 "id == null" 기본 판단을 쓸 수 없다.
     * 대신 감사값(createdAt)으로 판별해, 신규 저장 시 불필요한 SELECT(select-before-insert)를 막는다.
     */
    @Override
    @Transient
    public boolean isNew() {
        return createdAt == null;
    }
}
