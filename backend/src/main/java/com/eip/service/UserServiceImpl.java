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

import com.eip.util.UserUtils;
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
        userId = UserUtils.normalizeUserId(userId);
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
        operatorId = UserUtils.normalizeUserId(operatorId);
        verifyAdmin(operatorId);
        
        if (user.getUserId() != null) {
            user.setUserId(UserUtils.normalizeUserId(user.getUserId()));
        }
        if (user.getManager() != null && user.getManager().getUserId() != null) {
            user.getManager().setUserId(UserUtils.normalizeUserId(user.getManager().getUserId()));
        }

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
        operatorId = UserUtils.normalizeUserId(operatorId);
        verifyAdmin(operatorId);

        if (user.getUserId() != null) {
            user.setUserId(UserUtils.normalizeUserId(user.getUserId()));
        }
        if (user.getManager() != null && user.getManager().getUserId() != null) {
            user.getManager().setUserId(UserUtils.normalizeUserId(user.getManager().getUserId()));
        }

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
        final String normalizedUserId = UserUtils.normalizeUserId(userId);
        final String normalizedOperatorId = UserUtils.normalizeUserId(operatorId);
        verifyAdmin(normalizedOperatorId);

        User user = userRepository.findById(normalizedUserId)
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在：" + normalizedUserId));

        String newHash = hashPassword(newPassword);
        if (newHash.equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("新密碼不可與舊密碼相同");
        }

        user.setPasswordHash(newHash);
        userRepository.save(user);

        saveAuditLog(normalizedOperatorId, "UPDATE_PASSWORD", normalizedUserId, "變更員工編號 " + normalizedUserId + " 的密碼");
        log.info("管理員 {} 成功變更員工 {} 的密碼", normalizedOperatorId, normalizedUserId);
    }

    @Override
    @Transactional
    public void deleteUser(String userId, String operatorId) {
        final String normalizedUserId = UserUtils.normalizeUserId(userId);
        final String normalizedOperatorId = UserUtils.normalizeUserId(operatorId);
        verifyAdmin(normalizedOperatorId);

        User user = userRepository.findById(normalizedUserId)
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在：" + normalizedUserId));

        userRepository.delete(user);

        saveAuditLog(normalizedOperatorId, "DELETE_USER", normalizedUserId, "刪除員工帳號：" + normalizedUserId);
        log.info("管理員 {} 成功刪除員工帳號：{}", normalizedOperatorId, normalizedUserId);
    }

    @Override
    public User getUserById(String userId) {
        userId = UserUtils.normalizeUserId(userId);
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
        final String normalizedOperatorId = UserUtils.normalizeUserId(operatorId);
        User operator = userRepository.findById(normalizedOperatorId)
                .orElseThrow(() -> new IllegalArgumentException("操作員不存在：" + normalizedOperatorId));
        if (!"ADMIN".equalsIgnoreCase(operator.getRole())) {
            log.warn("權限拒絕：非管理員帳號 {} 嘗試進行敏感操作", normalizedOperatorId);
            throw new SecurityException("只有系統管理員 (ADMIN) 可以執行此操作");
        }
    }

    private void saveAuditLog(String operatorId, String actionType, String targetId, String description) {
        operatorId = UserUtils.normalizeUserId(operatorId);
        targetId = UserUtils.normalizeUserId(targetId);
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
