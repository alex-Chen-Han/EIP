package com.eip.controller;

import com.eip.entity.AuditLog;
import com.eip.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<?> getAllLogs(@RequestHeader("X-User-Id") String operatorId) {
        try {
            List<AuditLog> logs = auditLogService.getAllLogs(operatorId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("查詢審計日誌失敗", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportLogs(@RequestHeader("X-User-Id") String operatorId) {
        try {
            String csvData = auditLogService.exportLogsToCsv(operatorId);
            byte[] csvBytes = csvData.getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
            headers.setContentDispositionFormData("attachment", "audit_logs.csv");
            headers.setContentLength(csvBytes.length);

            log.info("管理員 {} 下載審計日誌 CSV 成功", operatorId);
            return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("匯出審計日誌失敗", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }
}
