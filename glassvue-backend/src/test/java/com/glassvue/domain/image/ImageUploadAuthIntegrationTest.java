package com.glassvue.domain.image;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.storage.FileStorageService;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미지 업로드 권한 회귀 테스트.
 *
 * <p>2026-07-20 이전엔 `POST /api/images`가 **ADMIN 전용**이었다(상품 이미지가 유일한 용도였을 때의 규칙).
 * 포토 리뷰가 생기면서 일반 사용자도 올려야 하는데, 이 규칙이 남아 있으면 리뷰에 사진을 첨부하는 순간
 * 403이 난다 — 실제로 E2E에서 이 상태로 막혔다. 권한 규칙은 조용히 되돌아가기 쉬워 테스트로 고정한다.
 *
 * <p>DB_HOST 있을 때만 실행(= .env 소싱). @Transactional 롤백 → 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageUploadAuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired FileStorageService fileStorageService;

    /**
     * 업로드가 실제로 만든 파일들. **@Transactional은 DB만 롤백하고 파일 쓰기는 되돌리지 않는다** —
     * 정리하지 않으면 업로드 디렉토리에 8바이트짜리 테스트 파일이 계속 쌓인다(2026-07-20 실제로 5개 발견).
     * DB row가 없어 고아 이미지 스위퍼도 못 잡으므로 테스트가 스스로 치운다.
     */
    private final List<String> uploadedUrls = new ArrayList<>();

    private static final String PW = "password123";
    private String userLoginId;

    @AfterEach
    void cleanUploadedFiles() {
        uploadedUrls.forEach(fileStorageService::delete);
        uploadedUrls.clear();
    }

    /** 1x1 PNG — 내용은 중요하지 않고 멀티파트로 받아지는지만 본다. */
    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "zz.png", MediaType.IMAGE_PNG_VALUE, new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A });
    }

    @BeforeEach
    void setUp() {
        userLoginId = "zzuser_" + UUID.randomUUID().toString().substring(0, 8);
        memberRepository.save(Member.builder()
                .loginId(userLoginId).password(passwordEncoder.encode(PW))
                .nickname("ZZ일반사용자").role(Role.USER).build());
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    @DisplayName("일반 사용자(USER)도 이미지를 업로드할 수 있다 — 포토 리뷰의 전제")
    void userCanUpload() throws Exception {
        String token = "Bearer " + login(userLoginId);
        String body = mockMvc.perform(multipart("/api/images").file(pngFile()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.url").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        uploadedUrls.add(JsonPath.read(body, "$.data.url")); // @AfterEach가 파일 삭제
    }

    @Test
    @DisplayName("비로그인 업로드는 여전히 막는다")
    void anonymousCannotUpload() throws Exception {
        mockMvc.perform(multipart("/api/images").file(pngFile()))
                .andExpect(status().isUnauthorized());
    }
}
