package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.util.FileUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 상품 이미지 업로드 — 파일을 저장하고 화면에서 쓸 URL(/images/파일명)을 돌려줌.
 * 상품 등록 API는 JSON(imageUrl)을 받으므로, 화면에서 먼저 이 API로 올린 뒤 받은 URL을 imageUrl에 담아 등록한다.
 * (로그인 필요: SecurityConfig의 /api/items/** 비GET 요청 authenticated 규칙 적용)
 */
@RestController
public class ItemImageController {

    @PostMapping("/api/items/images")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("image") MultipartFile image) {
        String savedFilename = FileUtil.saveImage(image);
        if (savedFilename == null) {
            throw new BadRequestException("업로드할 이미지 파일을 선택해주세요.");
        }
        return ResponseEntity.ok(Map.of("imageUrl", "/images/" + savedFilename));
    }
}
