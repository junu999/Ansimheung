package com.slink.ansimheung.hazard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.auth.User;
import com.slink.ansimheung.auth.UserRepository;
import com.slink.ansimheung.common.type.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제보 사진 (#24, DB 필요): 형식 검사 · EXIF 제거 · 1280px · 방향 보정 · 보기 권한(P-08).
 * 테스트 설정은 승인 대기(auto-approve false), 파일은 build/test-uploads (롤백되면 파일도 지워짐)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PhotoApiTest {

    private static final Path UPLOADS = Path.of("build/test-uploads");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired HazardRepository hazards;
    @Autowired PhotoRepository photos;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    LoginUser member;
    LoginUser other;
    LoginUser admin;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hazards");   // bootRun 으로 들어간 시드 등 (롤백되므로 그대로 남음)
        member = LoginUser.from(users.save(User.create("photo-test@ansimheung.test", "{noop}x", "학부모1", Role.USER)));
        other = LoginUser.from(users.save(User.create("photo-test2@ansimheung.test", "{noop}x", "학부모2", Role.USER)));
        admin = LoginUser.from(users.save(User.create("photo-admin@ansimheung.test", "{noop}x", "관리자1", Role.ADMIN)));
    }

    // ---------- 테스트용 사진 ----------

    private static byte[] image(int w, int h, String format) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w / 2, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** SOI 바로 뒤에 EXIF APP1(Orientation + GPS IFD: 위도 N 37.34) 을 끼워 넣은 JPEG */
    private static byte[] withExif(byte[] jpeg, int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(128);   // 빅엔디언 "MM"
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);                                                         // IFD0 항목 2개
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);          // GPS IFD 위치
        tiff.putInt(0);                                                                   // 다음 IFD 없음
        tiff.putShort((short) 2);                                                         // GPS IFD 항목 2개 (offset 38)
        tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
        tiff.putShort((short) 0x0002).putShort((short) 2).putInt(4).put("37.3".getBytes(StandardCharsets.US_ASCII));
        tiff.putInt(0);
        byte[] tiffBytes = new byte[tiff.position()];
        tiff.flip().get(tiffBytes);

        byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int length = 2 + exifHeader.length + tiffBytes.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);                                                            // FFD8
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiffBytes);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static boolean contains(byte[] data, String ascii) {
        byte[] needle = ascii.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private ResultActions upload(LoginUser as, MockMultipartFile photo) throws Exception {
        return mockMvc.perform(multipart("/api/reports").file(photo)
                .param("lat", "37.3413").param("lng", "126.7436")
                .param("type", "ILLEGAL_PARKING").param("timeSlot", "MORNING")
                .with(user(as)).with(csrf()));
    }

    /** 업로드 → 저장된 photos 행 */
    private Photo uploaded(LoginUser as, MockMultipartFile photo) throws Exception {
        upload(as, photo).andExpect(status().isCreated());
        em.flush();
        return photos.findAll().get(0);
    }

    private static byte[] stored(Photo photo) throws IOException {
        return Files.readAllBytes(UPLOADS.resolve(photo.getFilePath()));
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    // ---------- 업로드 ----------

    @Test
    void GPS가_든_JPG를_올리면_EXIF_없이_UUID_파일명으로_저장되고_승인_대기() throws Exception {
        byte[] original = withExif(image(300, 200, "jpg"), 1);
        assertThat(contains(original, "Exif")).isTrue();                    // 테스트 사진에 EXIF 가 있음을 먼저 확인
        assertThat(read(original)).isNotNull();

        Photo photo = uploaded(member, new MockMultipartFile("photo", "IMG_0001.jpg", "image/jpeg", original));

        assertThat(photo.getFilePath()).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(photo.getContentType()).isEqualTo("image/jpeg");
        assertThat(photo.isApproved()).isFalse();
        byte[] saved = stored(photo);
        assertThat(contains(saved, "Exif")).isFalse();
        assertThat(ExifOrientation.orientation(saved)).isEqualTo(1);
        assertThat(read(saved).getWidth()).isEqualTo(300);
    }

    @Test
    void 긴_변은_1280px로_줄이고_PNG는_PNG로_저장() throws Exception {
        Photo photo = uploaded(member, new MockMultipartFile("photo", "big.png", "image/png", image(2000, 1000, "png")));

        assertThat(photo.getFilePath()).endsWith(".png");
        assertThat(photo.getContentType()).isEqualTo("image/png");
        BufferedImage saved = read(stored(photo));
        assertThat(saved.getWidth()).isEqualTo(1280);
        assertThat(saved.getHeight()).isEqualTo(640);
    }

    @Test
    void 휴대폰_세로_사진_Orientation_6은_픽셀을_돌려서_저장() throws Exception {
        byte[] original = withExif(image(400, 200, "jpg"), 6);
        assertThat(ExifOrientation.orientation(original)).isEqualTo(6);

        BufferedImage saved = read(stored(uploaded(member, new MockMultipartFile("photo", "p.jpg", "image/jpeg", original))));
        assertThat(saved.getWidth()).isEqualTo(200);
        assertThat(saved.getHeight()).isEqualTo(400);
    }

    @Test
    void 이름만_jpg인_파일과_깨진_PNG는_400_FILE_TYPE이고_아무것도_저장_안_됨() throws Exception {
        byte[] brokenPng = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
        for (MockMultipartFile bad : new MockMultipartFile[]{
                new MockMultipartFile("photo", "a.jpg", "image/jpeg", "not an image".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("photo", "b.png", "image/png", brokenPng),
                new MockMultipartFile("photo", "c.gif", "image/gif", image(10, 10, "gif"))}) {
            upload(member, bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("FILE_TYPE"));
        }
        assertThat(hazards.count()).isZero();
        assertThat(photos.count()).isZero();
    }

    // ---------- 보기 ----------

    @Test
    void 미승인은_본인_관리자만_승인되면_누구나_숨김_지점은_관리자만() throws Exception {
        Photo photo = uploaded(member, new MockMultipartFile("photo", "a.jpg", "image/jpeg", image(50, 50, "jpg")));
        String url = "/api/photos/" + photo.getId();
        byte[] saved = stored(photo);

        mockMvc.perform(get(url)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get(url).with(user(other))).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(user(member)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/jpeg"))
                .andExpect(content().bytes(saved));
        mockMvc.perform(get(url).with(user(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/api/photos/999999").with(user(admin))).andExpect(status().isNotFound());

        photo.setApproved(true);
        em.flush();
        mockMvc.perform(get(url)).andExpect(status().isOk());

        Hazard hazard = hazards.findAll().get(0);
        hazard.hide("테스트");
        em.flush();
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(user(member))).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(user(admin))).andExpect(status().isOk());
    }

    @Test
    void 공개_상세에는_승인된_사진만_나온다() throws Exception {
        Photo photo = uploaded(member, new MockMultipartFile("photo", "a.jpg", "image/jpeg", image(50, 50, "jpg")));
        String detail = "/api/hazards/" + hazards.findAll().get(0).getId();

        mockMvc.perform(get(detail)).andExpect(jsonPath("$.photos.length()").value(0));
        photo.setApproved(true);
        em.flush();
        mockMvc.perform(get(detail))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].id").value(photo.getId()))
                .andExpect(jsonPath("$.photos[0].approved").value(true));
    }
}
