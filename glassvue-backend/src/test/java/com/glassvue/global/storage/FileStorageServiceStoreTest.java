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

    /**
     * 축소가 의미 있도록 <b>충분히 큰</b> PNG 원본. 고주파 패턴이라 PNG로 잘 안 눌려 원본이 크고,
     * 축소 WebP는 훨씬 작아진다(= 파생본 유지 기준을 넉넉히 넘긴다).
     */
    private static byte[] bigPng() throws Exception {
        BufferedImage img = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 900; y++) {
            for (int x = 0; x < 1200; x++) {
                img.setRGB(x, y, ((x * 7 + y * 13) % 256) << 16 | ((x * 3 + y * 11) % 256) << 8 | ((x + y * 5) % 256));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** 파생본을 만들 가치가 없는 작은 원본(단색 소형). */
    private static byte[] tinyPng() throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 60, 60));
        g.fillRect(0, 0, 40, 30);
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

    @Test
    @DisplayName("작은 원본은 파생본을 만들지 않는다 — 줄일 게 없어 저장만 늘기 때문")
    void store_skipsDerivativesForSmallOriginal(@TempDir Path dir) throws Exception {
        byte[] tiny = tinyPng();
        MockMultipartFile file = new MockMultipartFile("file", "tiny.png", "image/png", tiny);

        FileStorageService.Stored stored = service(dir).store(file);

        // 원본은 정상 저장되지만 파생본 URL은 없다 → 응답이 원본으로 폴백한다.
        assertThat(stored.url()).endsWith(".png");
        assertThat(stored.mediumUrl()).isNull();
        assertThat(stored.thumbUrl()).isNull();
        // 디스크에도 파생본 파일이 생기지 않았다(원본 1개뿐).
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.endsWith(".webp"));
        }
    }

    @Test
    @DisplayName("백필: 이미 저장된 원본에서 파생본을 만든다")
    void generateDerivatives_fromExistingOriginal(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("abc.png"), bigPng()); // 파생본 도입 전에 올라온 원본이라고 가정

        FileStorageService.Derivatives d = service(dir).generateDerivatives("/uploads/abc.png");

        assertThat(d.none()).isFalse();
        assertThat(d.mediumUrl()).isEqualTo("/uploads/abc_m.webp");
        assertThat(d.thumbUrl()).isEqualTo("/uploads/abc_t.webp");
        assertThat(dir.resolve("abc_m.webp")).exists();
        assertThat(dir.resolve("abc_t.webp")).exists();
    }

    @Test
    @DisplayName("백필: 원본 파일이 없으면 건너뛴다(예외 없이 빈 결과)")
    void generateDerivatives_missingOriginal(@TempDir Path dir) {
        FileStorageService.Derivatives d = service(dir).generateDerivatives("/uploads/nope.png");

        assertThat(d.none()).isTrue();
    }

    @Test
    @DisplayName("백필: 업로드 디렉토리 밖 경로는 거부한다")
    void generateDerivatives_rejectsTraversal(@TempDir Path parent) throws Exception {
        Path dir = Files.createDirectory(parent.resolve("uploads"));
        Files.write(parent.resolve("outside.png"), bigPng());

        FileStorageService.Derivatives d = service(dir).generateDerivatives("/uploads/../outside.png");

        assertThat(d.none()).isTrue();
    }
}
