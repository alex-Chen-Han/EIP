package com.eip.service;

import com.eip.entity.AuditLog;
import com.eip.entity.Department;
import com.eip.entity.User;
import com.eip.repository.AuditLogRepository;
import com.eip.repository.DepartmentRepository;
import com.eip.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    public User authenticate(String userId, String rawPassword) {
        log.info("嘗試登入：{}", userId);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("登入失敗：找不到使用者 {}", userId);
            return null;
        }

        String hash = hashPassword(rawPassword);
        if (hash.equals(user.getPasswordHash())) {
            log.info("登入成功：{}", userId);
            return user;
        } else {
            log.warn("登入失敗：密碼錯誤 {}", userId);
            return null;
        }
    }

    @Override
    @Transactional
    public User createUser(User user, String rawPassword, String operatorId) {
        verifyAdmin(operatorId);
        
        if (userRepository.existsById(user.getUserId())) {
            log.warn("建立帳號失敗：帳號已存在 {}", user.getUserId());
            throw new IllegalArgumentException("員工編號已存在：" + user.getUserId());
        }

        user.setPasswordHash(hashPassword(rawPassword));
        User savedUser = userRepository.save(user);

        saveAuditLog(operatorId, "CREATE_USER", user.getUserId(), 
                String.format("建立新員工帳號: %s (姓名: %s, 角色: %s)", 
                        user.getUserId(), user.getRealName(), user.getRole()));
        
        log.info("管理員 {} 成功建立員工帳號：{}", operatorId, user.getUserId());
        return savedUser;
    }

    @Override
    @Transactional
    public User updateUser(User user, String operatorId) {
        verifyAdmin(operatorId);

        User existingUser = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在：" + user.getUserId()));

        // 更新基本欄位
        existingUser.setRealName(user.getRealName());
        existingUser.setIdNumber(user.getIdNumber());
        existingUser.setBankAccount(user.getBankAccount());
        existingUser.setDepartment(user.getDepartment());
        existingUser.setPosition(user.getPosition());
        existingUser.setRole(user.getRole());
        existingUser.setManager(user.getManager());

        User savedUser = userRepository.save(existingUser);

        saveAuditLog(operatorId, "UPDATE_USER", user.getUserId(), 
                String.format("更新員工基本資料: %s (姓名: %s, 角色: %s)", 
                        user.getUserId(), user.getRealName(), user.getRole()));

        log.info("管理員 {} 成功更新員工帳號：{}", operatorId, user.getUserId());
        return savedUser;
    }

    @Override
    @Transactional
    public void changePassword(String userId, String newPassword, String operatorId) {
        verifyAdmin(operatorId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在：" + userId));

        user.setPasswordHash(hashPassword(newPassword));
        userRepository.save(user);

        saveAuditLog(operatorId, "UPDATE_PASSWORD", userId, "變更員工編號 " + userId + " 的密碼");
        log.info("管理員 {} 成功變更員工 {} 的密碼", operatorId, userId);
    }

    @Override
    @Transactional
    public void deleteUser(String userId, String operatorId) {
        verifyAdmin(operatorId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在：" + userId));

        userRepository.delete(user);

        saveAuditLog(operatorId, "DELETE_USER", userId, "刪除員工帳號：" + userId);
        log.info("管理員 {} 成功刪除員工帳號：{}", operatorId, userId);
    }

    @Override
    public User getUserById(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public List<User> getUsersByDept(String deptId) {
        return userRepository.findByDepartmentDeptId(deptId);
    }

    @Override
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    // 私有輔助方法
    private String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            log.error("SHA-256 加密失敗", ex);
            throw new RuntimeException("SHA-256 加密失敗", ex);
        }
    }

    private void verifyAdmin(String operatorId) {
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new IllegalArgumentException("操作員不存在：" + operatorId));
        if (!"ADMIN".equalsIgnoreCase(operator.getRole())) {
            log.warn("權限拒絕：非管理員帳號 {} 嘗試進行敏感操作", operatorId);
            throw new SecurityException("只有系統管理員 (ADMIN) 可以執行此操作");
        }
    }

    private void saveAuditLog(String operatorId, String actionType, String targetId, String description) {
        User operator = userRepository.findById(operatorId).orElse(null);
        if (operator != null) {
            AuditLog auditLog = AuditLog.builder()
                    .operator(operator)
                    .actionType(actionType)
                    .targetId(targetId)
                    .description(description)
                    .actionTime(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        }
    }
}
