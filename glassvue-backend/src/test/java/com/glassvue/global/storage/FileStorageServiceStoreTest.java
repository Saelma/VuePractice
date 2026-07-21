package com.glassvue.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 업로드 시 원본 저장 + 파생본(medium·thumb WebP) 생성을 검증한다.
 * 목록·상세에서 원본 풀사이즈 대신 축소본을 쓰게 하는 것이 이 기능의 목적이므로,
 * 파생본이 실제로 만들어지고(WebP) 원본보다 작아지는지까지 본다.
 */
class FileStorageServiceStoreTest {

    private FileStorageService service(Path dir) {
        return new FileStorageService(dir.toString(), "/uploads");
    }

    /** 축소가 의미 있도록 큰 PNG 원본을 만든다. */
    private static byte[] bigPng() throws Exception {
        BufferedImage img = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(30, 120, 200));
        g.fillRect(0, 0, 1200, 900);
        g.setColor(Color.WHITE);
        for (int i = 0; i < 40; i++) {
            g.fillOval(i * 25, (i * 17) % 800, 120, 120);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("업로드 → 원본 + medium/thumb WebP 3개 파일 생성, URL 반환")
    void store_createsOriginalAndDerivatives(@TempDir Path dir) throws Exception {
        byte[] png = bigPng();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png);

        FileStorageService.Stored stored = service(dir).store(file);

        // URL 형태
        assertThat(stored.url()).startsWith("/uploads/").endsWith(".png");
        assertThat(stored.mediumUrl()).startsWith("/uploads/").endsWith("_m.webp");
        assertThat(stored.thumbUrl()).startsWith("/uploads/").endsWith("_t.webp");

        // 디스크에 3개 파일
        Path original = dir.resolve(stored.url().substring("/uploads/".length()));
        Path medium = dir.resolve(stored.mediumUrl().substring("/uploads/".length()));
        Path thumb = dir.resolve(stored.thumbUrl().substring("/uploads/".length()));
        assertThat(original).exists();
        assertThat(medium).exists();
        assertThat(thumb).exists();

        // 파생본은 WebP(RIFF....WEBP) 이고 원본보다 작다
        byte[] mediumBytes = Files.readAllBytes(medium);
        assertThat(new String(mediumBytes, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(mediumBytes, 8, 4)).isEqualTo("WEBP");
        assertThat(Files.size(thumb)).isLessThan(Files.size(medium));
        assertThat(Files.size(medium)).isLessThan((long) png.length);
    }
}
