package com.glassvue.global.storage;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 파일을 디스크에 저장하고 서빙 URL을 돌려준다. (nginx가 url-prefix를 dir로 서빙)
 * 검증: ① 원본 확장자 화이트리스트 + ② 매직바이트(파일 시그니처)로 실제 내용 확인.
 * (content-type·확장자는 위조 가능하므로 시그니처가 최종 판정)
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final Path dir;
    private final String urlPrefix;

    public FileStorageService(
            @Value("${app.upload.dir}") String dir,
            @Value("${app.upload.url-prefix}") String urlPrefix) {
        this.dir = Path.of(dir);
        this.urlPrefix = urlPrefix;
        try {
            Files.createDirectories(this.dir);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉토리 생성 실패: " + dir, e);
        }
    }

    public record Stored(String url, String originalName, String contentType, long size) {
    }

    public Stored store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "빈 파일입니다.");
        }
        // ① 원본 확장자 화이트리스트
        String originalName = file.getOriginalFilename();
        if (!ALLOWED_EXTENSIONS.contains(extensionOf(originalName))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 확장자입니다. (jpg/jpeg/png/gif/webp)");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("파일 읽기 실패", e);
        }

        // ② 매직바이트로 실제 이미지인지 확인 (확장자·content-type 위조 방지). 저장 확장자는 여기서 결정.
        String ext = detectImageExtension(bytes);
        if (ext == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효한 이미지 파일이 아닙니다. (jpg/png/gif/webp)");
        }

        String filename = UUID.randomUUID() + ext;
        try {
            Path target = dir.resolve(filename);
            Files.write(target, bytes);
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--")); // nginx 읽기
            return new Stored(urlPrefix + "/" + filename, originalName, file.getContentType(), (long) bytes.length);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패", e);
        }
    }

    /**
     * 저장된 파일을 지운다. 없으면 조용히 넘어간다(이미 지워진 경우 = 목표 상태 달성).
     *
     * <p>url은 {@code store()}가 돌려준 {@code {urlPrefix}/{uuid}.{ext}} 형태다.
     * 마지막 경로 조각만 파일명으로 쓰고, {@code dir} 밖으로 나가는 경로는 거부한다
     * (url이 DB에서 오므로 경로 조작 여지를 남기지 않는다).
     *
     * @return 실제로 지웠으면 true
     */
    public boolean delete(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.contains("..")) {
            return false;
        }
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir)) {
            return false; // 업로드 디렉토리 밖 → 건드리지 않는다
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            // 정리 작업이라 실패해도 흐름을 막지 않는다. 다음 주기에 다시 시도된다.
            return false;
        }
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 파일 시그니처로 이미지 종류를 판별. 이미지 아니면 null. */
    private static String detectImageExtension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ".jpg"; // JPEG
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return ".png";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return ".gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return ".webp";
        }
        return null;
    }
}
