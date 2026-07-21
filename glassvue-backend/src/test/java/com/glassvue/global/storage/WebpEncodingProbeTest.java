package com.glassvue.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/**
 * scrimage-webp의 번들 cwebp 바이너리가 이 서버(Rocky 9 x86_64)에서 실제로 도는지 확인하는 de-risk 테스트.
 * WebP 파생본 파이프라인을 본격 구현하기 전에, 인코딩이 되는지부터 실측한다.
 * (실패하면 시스템 cwebp 설치 등 다른 경로로 틀어야 한다.)
 */
class WebpEncodingProbeTest {

    @Test
    void scrimage로_WebP_인코딩이_된다() throws Exception {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(80, 160, 240));
        g.fillRect(0, 0, 400, 300);
        g.setColor(Color.WHITE);
        g.fillOval(120, 90, 160, 120);
        g.dispose();

        ImmutableImage image = ImmutableImage.wrapAwt(img);

        // 썸네일 200px로 줄여서 WebP로 인코딩
        byte[] webp = image.bound(200, 200).bytes(WebpWriter.DEFAULT);

        assertThat(webp).isNotEmpty();
        // WebP 매직: "RIFF"...."WEBP"
        assertThat(new String(webp, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(webp, 8, 4)).isEqualTo("WEBP");
    }
}
