package com.eip.service;

import com.eip.entity.AuditLog;
import java.util.List;

public interface AuditLogService {
    void saveLog(String operatorId, String actionType, String targetId, String description);
    List<AuditLog> getAllLogs(String operatorId);
    String exportLogsToCsv(String operatorId);
}
