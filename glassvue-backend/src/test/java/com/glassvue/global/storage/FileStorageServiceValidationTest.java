package com.glassvue.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 업로드 <b>거부 경로</b> (H-8, 2026-07-31).
 *
 * <p><b>왜 이 파일이 생겼나</b>: H-5(JaCoCo) 실측에서 {@code FileStorageService} 분기 34%,
 * 그중 {@code detectImageExtension} 이 <b>35% · 분기 18%</b> 였다. 기존
 * {@link FileStorageServiceStoreTest}·{@link FileStorageServiceDeleteTest} 는
 * <b>통과 경로와 경로 탈출(..)</b>만 본다 — 즉 <b>"막는 코드가 실제로 막는가"</b> 는
 * PNG 한 갈래 말고는 확인된 적이 없었다.
 *
 * <p>⚠ 착수 전 실측에서 <b>백로그 문장 하나가 틀린 것도 드러났다</b> — 백로그는
 * *"확장자·매직바이트·`..` 방어가 전부 미검증"* 이라고 적었지만 <b>`..` 은 이미 덮여 있었다</b>
 * (백필·삭제 양쪽). 분기 % 만 보고 결론을 적으면 이렇게 틀린다(WA §3-2).
 *
 * <p>여기서 못박는 것은 <b>"내용이 최종 판정"</b> 이라는 규칙이다 — 확장자와 content-type 은
 * 클라이언트가 마음대로 붙이므로, 저장 여부도 저장 확장자도 <b>파일 시그니처</b>가 정한다.
 */
class FileStorageServiceValidationTest {

    private FileStorageService service(Path dir) {
        return new FileStorageService(dir.toString(), "/uploads");
    }

    // 실제 이미지가 아니어도 된다 — 시그니처만 맞으면 저장되고, 10KB 미만이라 파생본은 건너뛴다.
    private static byte[] magic(int... head) {
        byte[] b = new byte[head.length];
        for (int i = 0; i < head.length; i++) {
            b[i] = (byte) head[i];
        }
        return b;
    }

    private static byte[] jpeg() {
        return magic(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0);
    }

    private static byte[] png() {
        return magic(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0);
    }

    private static byte[] gif() {
        return magic('G', 'I', 'F', '8', '9', 'a', 0, 0);
    }

    private static byte[] webp() {
        return magic('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');
    }

    private void assertRejected(Runnable call, String messagePart) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    assertThat(e.getMessage()).contains(messagePart);
                });
    }

    // ── ① 확장자 화이트리스트 ─────────────────────────────────

    @Test
    @DisplayName("허용되지 않은 확장자는 **내용을 보기 전에** 거부한다")
    void rejectsDisallowedExtension(@TempDir Path dir) {
        // 내용은 진짜 PNG 시그니처다 — 그래도 이름이 .exe 면 안 받는다(1차 관문).
        MockMultipartFile file = new MockMultipartFile("file", "payload.exe", "image/png", png());

        assertRejected(() -> service(dir).store(file), "허용되지 않은 확장자");
    }

    @Test
    @DisplayName("확장자가 아예 없는 이름도 거부한다")
    void rejectsNameWithoutExtension(@TempDir Path dir) {
        MockMultipartFile file = new MockMultipartFile("file", "noextension", "image/png", png());

        assertRejected(() -> service(dir).store(file), "허용되지 않은 확장자");
    }

    @Test
    @DisplayName("파일명이 null 이어도 터지지 않고 거부한다 — 클라이언트가 안 보낼 수 있다")
    void rejectsNullFilename(@TempDir Path dir) {
        MockMultipartFile file = new MockMultipartFile("file", null, "image/png", png());

        assertRejected(() -> service(dir).store(file), "허용되지 않은 확장자");
    }

    @Test
    @DisplayName("대문자 확장자(.PNG)는 **받는다** — 소문자로 정규화해 비교하므로")
    void acceptsUppercaseExtension(@TempDir Path dir) {
        MockMultipartFile file = new MockMultipartFile("file", "PHOTO.PNG", "image/png", png());

        // 안 받으면 아이폰·윈도우에서 올린 사진이 이유 없이 거부된다.
        assertThat(service(dir).store(file).url()).endsWith(".png");
    }

    @Test
    @DisplayName("빈 파일은 거부한다")
    void rejectsEmptyFile(@TempDir Path dir) {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        assertRejected(() -> service(dir).store(file), "빈 파일");
    }

    // ── ② 매직바이트가 최종 판정 ──────────────────────────────

    @Test
    @DisplayName("⚠ 이름만 이미지인 파일은 거부한다 — 확장자·content-type 은 위조 가능하다")
    void rejectsNonImageContentWithImageName(@TempDir Path dir) {
        byte[] notAnImage = "#!/bin/sh\necho hi\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", notAnImage);

        assertRejected(() -> service(dir).store(file), "유효한 이미지 파일이 아닙니다");
        assertThat(dir.toFile().list()).isEmpty();   // 거부된 파일은 디스크에 남지 않는다
    }

    @Test
    @DisplayName("시그니처를 판정할 만큼 짧은 파일도 거부한다(경계)")
    void rejectsTooShortFile(@TempDir Path dir) {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {(byte) 0xFF});

        assertRejected(() -> service(dir).store(file), "유효한 이미지 파일이 아닙니다");
    }

    @Test
    @DisplayName("⚠ 저장 확장자는 **이름이 아니라 내용**이 정한다 — 이름 photo.jpg + PNG 내용 → .png 로 저장")
    void savedExtensionFollowsContentNotName(@TempDir Path dir) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", png());

        FileStorageService.Stored stored = service(dir).store(file);

        // 이름을 따라 .jpg 로 저장하면 nginx 가 잘못된 Content-Type 으로 서빙한다.
        assertThat(stored.url()).endsWith(".png");
        assertThat(stored.originalName()).isEqualTo("photo.jpg");   // 원본 이름은 그대로 보존
        assertThat(Files.list(dir).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("네 포맷(JPEG·PNG·GIF·WebP) 시그니처를 각각 알아본다")
    void detectsAllSupportedSignatures(@TempDir Path dir) {
        FileStorageService service = service(dir);

        assertThat(service.store(new MockMultipartFile("f", "a.jpg", null, jpeg())).url()).endsWith(".jpg");
        assertThat(service.store(new MockMultipartFile("f", "b.png", null, png())).url()).endsWith(".png");
        assertThat(service.store(new MockMultipartFile("f", "c.gif", null, gif())).url()).endsWith(".gif");
        assertThat(service.store(new MockMultipartFile("f", "d.webp", null, webp())).url()).endsWith(".webp");
    }

    @Test
    @DisplayName("RIFF 이지만 WEBP 가 아닌 컨테이너(WAVE)는 거부한다 — 앞 4바이트만 보면 통과해 버린다")
    void rejectsRiffThatIsNotWebp(@TempDir Path dir) {
        byte[] wave = magic('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E');
        MockMultipartFile file = new MockMultipartFile("file", "sound.webp", "image/webp", wave);

        assertRejected(() -> service(dir).store(file), "유효한 이미지 파일이 아닙니다");
    }
}
