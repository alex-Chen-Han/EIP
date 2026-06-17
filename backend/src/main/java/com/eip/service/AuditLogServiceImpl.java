package com.eip.service;

import com.eip.entity.AuditLog;
import com.eip.entity.User;
import com.eip.repository.AuditLogRepository;
import com.eip.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void saveLog(String operatorId, String actionType, String targetId, String description) {
        User operator = userRepository.findById(operatorId).orElse(null);
        if (operator != null) {
            AuditLog auditLog = AuditLog.builder()
                    .operator(operator)
                    .actionType(actionType)
                    .targetId(targetId)
                    .description(description)
                    .build();
            auditLogRepository.save(auditLog);
        }
    }

    @Override
    public List<AuditLog> getAllLogs(String operatorId) {
        verifyAdmin(operatorId);
        return auditLogRepository.findAllByOrderByActionTimeDesc();
    }

    @Override
    @Transactional
    public String exportLogsToCsv(String operatorId) {
        verifyAdmin(operatorId);
        
        log.info("管理員 {} 正在匯出系統審計日誌...", operatorId);
        List<AuditLog> logs = auditLogRepository.findAllByOrderByActionTimeDesc();

        // 加上 UTF-8 BOM，防止 Excel 開啟時產生亂碼
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("日誌編號,操作員帳號,操作員姓名,操作類型,受影響對象,詳細變更說明,操作時間\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (AuditLog audit : logs) {
            csv.append(audit.getLogId()).append(",")
               .append(escapeCsvField(audit.getOperator().getUserId())).append(",")
               .append(escapeCsvField(audit.getOperator().getRealName())).append(",")
               .append(escapeCsvField(audit.getActionType())).append(",")
               .append(escapeCsvField(audit.getTargetId())).append(",")
               .append(escapeCsvField(audit.getDescription())).append(",")
               .append(audit.getActionTime().format(formatter)).append("\n");
        }

        saveLog(operatorId, "EXPORT_LOGS", null, "匯出系統審計操作日誌");
        log.info("管理員 {} 成功匯出 {} 筆審計日誌", operatorId, logs.size());
        return csv.toString();
    }

    private void verifyAdmin(String operatorId) {
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new IllegalArgumentException("操作人員不存在：" + operatorId));
        if (!"ADMIN".equalsIgnoreCase(operator.getRole())) {
            log.warn("越權存取阻斷：非管理員 {} 嘗試讀取/匯出審計日誌", operatorId);
            throw new SecurityException("只有系統管理員 (ADMIN) 可以存取審計日誌");
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        String value = field.replace("\"", "\"\"");
        if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            return "\"" + value + "\"";
        }
        return value;
    }
}
