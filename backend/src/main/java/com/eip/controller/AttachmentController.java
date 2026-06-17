package com.eip.controller;

import com.eip.entity.ApprovalAttachment;
import com.eip.entity.ApprovalForm;
import com.eip.repository.ApprovalAttachmentRepository;
import com.eip.service.BlobStorageService;
import com.eip.service.FormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AttachmentController {

    private final ApprovalAttachmentRepository attachmentRepository;
    private final FormService formService;
    private final BlobStorageService storageService;

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadAttachment(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        try {
            ApprovalAttachment attachment = attachmentRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("找不到該附件：" + id));

            // 安全性防線：藉由查詢 FormDetail 來驗證當前使用者是否擁有該簽呈的讀取權限
            // 如果無權存取該簽呈單，此處會拋出 SecurityException
            formService.getFormDetail(attachment.getForm().getFormId(), userId);

            byte[] data = storageService.downloadFile(attachment.getFilePath());

            // 處理檔名中文編碼，防止下載檔名亂碼
            String encodedFileName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(attachment.getContentType()));
            headers.setContentLength(attachment.getFileSize());
            headers.setContentDispositionFormData("attachment", encodedFileName);

            log.info("使用者 {} 成功下載附件 ID: {}, 檔名: {}", userId, id, attachment.getFileName());
            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (SecurityException e) {
            log.warn("使用者 {} 下載附件 ID: {} 失敗：越權存取被封鎖", userId, id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            log.error("下載附件失敗", e);
            return ResponseEntity.badRequest().body("無法下載附件：" + e.getMessage());
        }
    }
}
