package com.slink.ansimheung.hazard;

import com.slink.ansimheung.auth.LoginUser;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 사진 보기 (#24). 누구나 호출 가능하고 권한은 PhotoService.load 에서 (미승인은 본인·관리자만, 아니면 404) */
@RestController
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/api/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id, @AuthenticationPrincipal LoginUser user) {
        PhotoService.PhotoFile file = photoService.load(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())   // 승인·권한이 바뀔 수 있어서 매번 확인
                .body(file.bytes());
    }
}
