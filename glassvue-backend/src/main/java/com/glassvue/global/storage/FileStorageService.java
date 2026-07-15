package com.glassvue.global.storage;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 이미지 파일을 디스크에 저장하고 서빙 URL을 돌려준다. (nginx가 url-prefix를 dir로 서빙) */
@Service
public class FileStorageService {

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
        String contentType = file.getContentType();
        if (file.isEmpty() || contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일만 업로드할 수 있습니다.");
        }
        String filename = UUID.randomUUID() + extension(contentType);
        try {
            Path target = dir.resolve(filename);
            file.transferTo(target);
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--")); // nginx 읽기
            return new Stored(urlPrefix + "/" + filename, file.getOriginalFilename(), contentType, file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패", e);
        }
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".img";
        };
    }
}
