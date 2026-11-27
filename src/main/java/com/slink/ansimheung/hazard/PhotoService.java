package com.slink.ansimheung.hazard;

import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.config.AnsimheungProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 제보 사진 (#24, P-04 · P-08 · V-12).
 * 형식은 파일 앞 바이트로 판별 → 픽셀만 다시 그려 저장(EXIF·GPS 등 메타데이터는 옮기지 않음) → 긴 변 1280px.
 * 파일은 uploads/{UUID}.jpg|png, 보기는 GET /api/photos/{id} 로만 (권한 확인)
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final PhotoRepository photos;
    private final ReportRepository reports;
    private final HazardRepository hazards;
    private final AnsimheungProperties.Upload upload;

    public PhotoService(PhotoRepository photos, ReportRepository reports, HazardRepository hazards,
                        AnsimheungProperties props) {
        this.photos = photos;
        this.reports = reports;
        this.hazards = hazards;
        this.upload = props.upload();
    }

    /** 다시 인코딩한 사진 (메타데이터 없음) */
    public record Prepared(byte[] bytes, String contentType, String extension) {
    }

    /** 보기 응답용 */
    public record PhotoFile(byte[] bytes, String contentType) {
    }

    /** 검사 + 재인코딩. DB 저장 전에 불러서, 형식이 틀리면 아무것도 저장되지 않게 한다. 빈 파일은 사진 없음 */
    public Prepared prepare(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        byte[] original;
        try {
            original = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FILE_TYPE);
        }
        boolean jpeg = startsWith(original, JPEG_MAGIC);
        if (!jpeg && !startsWith(original, PNG_MAGIC)) {
            throw new ApiException(ErrorCode.FILE_TYPE);
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(original));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            throw new ApiException(ErrorCode.FILE_TYPE);   // 깨진 파일·읽을 수 없는 JPEG(CMYK 등)
        }
        int quarterTurns = jpeg ? ExifOrientation.quarterTurnsClockwise(original) : 0;
        BufferedImage clean = redraw(image, quarterTurns, upload.maxLongSidePx(),
                jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(clean, jpeg ? "jpg" : "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return jpeg ? new Prepared(out.toByteArray(), "image/jpeg", "jpg")
                : new Prepared(out.toByteArray(), "image/png", "png");
    }

    /** 파일 쓰기 + photos 행. 바깥 트랜잭션이 롤백되면 쓴 파일도 지운다 */
    public Photo store(Long reportId, Prepared prepared) {
        String fileName = UUID.randomUUID() + "." + prepared.extension();
        Path path = dir().resolve(fileName);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, prepared.bytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteQuietly(path);
                    }
                }
            });
        }
        Photo photo = Photo.create(reportId, fileName, prepared.contentType());
        photo.setApproved(upload.autoApprove());
        return photos.save(photo);
    }

    /**
     * 승인된 사진은 누구나, 미승인은 올린 본인·관리자만 (P-08). 숨김·반려 지점·삭제된 제보의 사진은 관리자만.
     * 볼 수 없으면 있는지도 알리지 않도록 NOT_FOUND
     */
    @Transactional(readOnly = true)
    public PhotoFile load(Long photoId, LoginUser viewer) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!(viewer != null && viewer.isAdmin()) && !canView(photo, viewer)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        Path path = dir().resolve(photo.getFilePath()).normalize();
        if (!path.startsWith(dir()) || !Files.isRegularFile(path)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        try {
            return new PhotoFile(Files.readAllBytes(path), photo.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean canView(Photo photo, LoginUser viewer) {
        Report report = reports.findById(photo.getReportId()).orElse(null);
        if (report == null || report.isDeleted()) {
            return false;
        }
        Hazard hazard = hazards.findById(report.getHazardId()).orElse(null);
        if (hazard == null || hazard.isHidden() || hazard.getStatus() == HazardStatus.REJECTED) {
            return false;
        }
        return photo.isApproved() || (viewer != null && viewer.id().equals(report.getUserId()));
    }

    private Path dir() {
        return Path.of(upload.dir()).toAbsolutePath().normalize();
    }

    /** 방향 보정(시계 방향 90° × quarterTurns) → 긴 변 maxLongSide 이하로 축소 → 새 이미지에 픽셀만 그림 */
    static BufferedImage redraw(BufferedImage src, int quarterTurns, int maxLongSide, int imageType) {
        boolean swap = quarterTurns % 2 == 1;
        int w = swap ? src.getHeight() : src.getWidth();
        int h = swap ? src.getWidth() : src.getHeight();
        double scale = Math.min(1.0, (double) maxLongSide / Math.max(w, h));
        int outW = Math.max(1, (int) Math.round(w * scale));
        int outH = Math.max(1, (int) Math.round(h * scale));

        AffineTransform t = new AffineTransform();
        t.scale(scale, scale);
        switch (quarterTurns) {
            case 1 -> { t.translate(src.getHeight(), 0); t.rotate(Math.PI / 2); }
            case 2 -> { t.translate(src.getWidth(), src.getHeight()); t.rotate(Math.PI); }
            case 3 -> { t.translate(0, src.getWidth()); t.rotate(-Math.PI / 2); }
            default -> { }
        }
        BufferedImage out = new BufferedImage(outW, outH, imageType);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (imageType == BufferedImage.TYPE_INT_RGB) {
                g.setColor(Color.WHITE);   // 투명 픽셀이 JPEG 에서 검게 나오지 않게
                g.fillRect(0, 0, outW, outH);
            }
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // DB 에는 행이 없어서 열리지는 않음. 파일만 남으니 로그로 알림
            log.warn("롤백된 제보의 사진 파일을 지우지 못함: {}", path.getFileName(), e);
        }
    }
}
