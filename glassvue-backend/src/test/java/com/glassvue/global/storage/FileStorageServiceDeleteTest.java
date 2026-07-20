package com.glassvue.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 파일 삭제 — 특히 **업로드 디렉토리 밖을 건드리지 않는지**.
 * url은 DB에서 오지만, 정리 로직이 경로를 그대로 신뢰하면 사고 범위가 디스크 전체가 된다.
 */
class FileStorageServiceDeleteTest {

    private FileStorageService service(Path dir) {
        return new FileStorageService(dir.toString(), "/uploads");
    }

    @Test
    @DisplayName("업로드된 파일을 지운다")
    void deletesExistingFile(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.png"), "x");

        assertThat(service(dir).delete("/uploads/a.png")).isTrue();
        assertThat(Files.exists(dir.resolve("a.png"))).isFalse();
    }

    @Test
    @DisplayName("이미 없는 파일은 false — 예외로 흐름을 막지 않는다(정리 작업이므로)")
    void missingFileIsFalse(@TempDir Path dir) {
        assertThat(service(dir).delete("/uploads/nope.png")).isFalse();
    }

    @Test
    @DisplayName("null·빈 문자열은 무시한다")
    void blankIsIgnored(@TempDir Path dir) {
        assertThat(service(dir).delete(null)).isFalse();
        assertThat(service(dir).delete("")).isFalse();
    }

    @Test
    @DisplayName("상위 경로 탈출(..)은 거부하고 바깥 파일을 건드리지 않는다")
    void rejectsTraversal(@TempDir Path parent) throws IOException {
        Path dir = Files.createDirectory(parent.resolve("uploads"));
        Path outside = Files.writeString(parent.resolve("secret.txt"), "keep me");

        assertThat(service(dir).delete("/uploads/../secret.txt")).isFalse();
        assertThat(Files.exists(outside)).isTrue();
    }
}
