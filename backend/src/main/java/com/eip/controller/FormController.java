package com.eip.controller;

import com.eip.entity.ApprovalForm;
import com.eip.entity.ApprovalRoute;
import com.eip.service.FormService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import com.eip.util.UserUtils;

@Slf4j
@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class FormController {

    private final FormService formService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitForm(
            @RequestHeader("X-User-Id") String applicantId,
            @RequestPart("form") String formJson,
            @RequestPart("routes") String routesJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        try {
            applicantId = UserUtils.normalizeUserId(applicantId);
            // 解析 JSON
            ApprovalForm form = objectMapper.readValue(formJson, ApprovalForm.class);
            List<ApprovalRoute> routes = objectMapper.readValue(routesJson, new TypeReference<List<ApprovalRoute>>() {});
            
            ApprovalForm result = formService.submitForm(form, routes, files, applicantId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("送出簽呈單失敗", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<?> withdrawForm(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String applicantId) {
        try {
            applicantId = UserUtils.normalizeUserId(applicantId);
            ApprovalForm result = formService.withdrawForm(id, applicantId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("抽單失敗", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveRoute(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String approverId,
            @RequestBody Map<String, String> body) {
        try {
            approverId = UserUtils.normalizeUserId(approverId);
            String comment = body.getOrDefault("comment", "同意");
            ApprovalForm result = formService.approveRoute(id, approverId, comment);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("核准簽呈失敗", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectRoute(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String approverId,
            @RequestBody Map<String, String> body) {
        try {
            approverId = UserUtils.normalizeUserId(approverId);
            String comment = body.get("comment");
            if (comment == null || comment.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("駁回時必須填寫意見");
            }
            ApprovalForm result = formService.rejectRoute(id, approverId, comment);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("駁回簽呈失敗", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalForm>> getPendingForms(@RequestHeader("X-User-Id") String userId) {
        userId = UserUtils.normalizeUserId(userId);
        return ResponseEntity.ok(formService.getPendingForms(userId));
    }

    @GetMapping("/reviewed")
    public ResponseEntity<List<ApprovalForm>> getReviewedForms(@RequestHeader("X-User-Id") String userId) {
        userId = UserUtils.normalizeUserId(userId);
        return ResponseEntity.ok(formService.getReviewedForms(userId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ApprovalForm>> getMyForms(@RequestHeader("X-User-Id") String userId) {
        userId = UserUtils.normalizeUserId(userId);
        return ResponseEntity.ok(formService.getMyForms(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getFormDetail(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        try {
            userId = UserUtils.normalizeUserId(userId);
            ApprovalForm form = formService.getFormDetail(id, userId);
            return ResponseEntity.ok(form);
        } catch (SecurityException e) {
            log.warn("越權讀取封鎖: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
