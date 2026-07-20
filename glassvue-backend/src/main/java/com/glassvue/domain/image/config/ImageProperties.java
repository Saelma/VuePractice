package com.glassvue.domain.image.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * image.* 설정.
 *
 * @param cleanupEnabled     고아 이미지 정리 스케줄러 사용 여부
 * @param cleanupGraceHours  업로드 후 이 시간이 지난 미사용 이미지만 지운다.
 *                           작성 중인 폼(업로드는 했지만 아직 저장 안 함)의 이미지를 뺏지 않기 위한 유예.
 */
@ConfigurationProperties(prefix = "image")
public record ImageProperties(boolean cleanupEnabled, long cleanupGraceHours) {
}
