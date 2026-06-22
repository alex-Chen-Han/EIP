package com.eip.service;

import com.eip.entity.AuditLog;
import com.eip.entity.Department;
import com.eip.entity.User;
import com.eip.repository.AuditLogRepository;
import com.eip.repository.DepartmentRepository;
import com.eip.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User adminOperator;
    private User employeeOperator;
    private Department dept;
    private String adminPasswordRaw = "AdminPass123";
    private String adminPasswordHash;

    @BeforeEach
    public void setup() {
        dept = Department.builder().deptId("D01").deptName("資訊部").build();
        adminPasswordHash = hashPassword(adminPasswordRaw);

        adminOperator = User.builder()
                .userId("ADMIN01")
                .passwordHash(adminPasswordHash)
                .realName("系統管理員")
                .role("ADMIN")
                .department(dept)
                .build();

        employeeOperator = User.builder()
                .userId("EMP001")
                .passwordHash(hashPassword("EmpPass123"))
                .realName("普通員工")
                .role("EMPLOYEE")
                .department(dept)
                .build();
    }

    @Test
    public void testAuthenticate_Success() {
        when(userRepository.findById("ADMIN01")).thenReturn(Optional.of(adminOperator));

        User result = userService.authenticate("ADMIN01", adminPasswordRaw);

        assertNotNull(result);
        assertEquals("ADMIN01", result.getUserId());
        assertEquals(adminPasswordHash, result.getPasswordHash());
    }

    @Test
    public void testAuthenticate_WrongPassword() {
        when(userRepository.findById("ADMIN01")).thenReturn(Optional.of(adminOperator));

        User result = userService.authenticate("ADMIN01", "WrongPassword");

        assertNull(result);
    }

    @Test
    public void testAuthenticate_UserNotFound() {
        when(userRepository.findById("NON_EXIST")).thenReturn(Optional.empty());

        User result = userService.authenticate("NON_EXIST", "Password");

        assertNull(result);
    }

    @Test
    public void testCreateUser_SuccessByAdmin() {
        User newUser = User.builder()
                .userId("EMP002")
                .realName("小華")
                .role("EMPLOYEE")
                .department(dept)
                .build();

        when(userRepository.findById("ADMIN01")).thenReturn(Optional.of(adminOperator));
        when(userRepository.existsById("EMP002")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(newUser, "NewPass123", "ADMIN01");

        assertNotNull(result);
        assertEquals("EMP002", result.getUserId());
        assertEquals(hashPassword("NewPass123"), result.getPasswordHash());

        // 驗證是否成功寫入 AuditLog 審計日誌
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        AuditLog savedLog = logCaptor.getValue();
        assertEquals("CREATE_USER", savedLog.getActionType());
        assertEquals("EMP002", savedLog.getTargetId());
        assertEquals("ADMIN01", savedLog.getOperator().getUserId());
    }

    @Test
    public void testCreateUser_DeniedByEmployee() {
        User newUser = User.builder()
                .userId("EMP002")
                .realName("小華")
                .role("EMPLOYEE")
                .department(dept)
                .build();

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(employeeOperator));

        assertThrows(SecurityException.class, () -> {
            userService.createUser(newUser, "NewPass123", "EMP001");
        });

        verify(userRepository, never()).save(any(User.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    public void testCreateUser_DuplicateId() {
        User newUser = User.builder()
                .userId("EMP001")
                .realName("重複員工")
                .role("EMPLOYEE")
                .department(dept)
                .build();

        when(userRepository.findById("ADMIN01")).thenReturn(Optional.of(adminOperator));
        when(userRepository.existsById("EMP001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(newUser, "NewPass123", "ADMIN01");
        });

        verify(userRepository, never()).save(any(User.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    public void testUpdateUser_SuccessByAdmin() {
        User existingUser = User.builder()
                .userId("EMP001")
                .realName("舊姓名")
                .role("EMPLOYEE")
                .department(dept)
                .build();

        User updateDetails = User.builder()
                .userId("EMP001")
                .realName("新姓名")
                .role("MANAGER")
                .department(dept)
                .build();

        when(userRepository.findById("ADMIN01")).thenReturn(Optional.of(adminOperator));
        when(userRepository.findById("EMP001")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUser(updateDetails, "ADMIN01");

        assertNotNull(result);
        assertEquals("新姓名", result.getRealName());
        assertEquals("MANAGER", result.getRole());

        // 驗證是否寫入 AuditLog 審計日誌
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        AuditLog savedLog = logCaptor.getValue();
        assertEquals("UPDATE_USER", savedLog.getActionType());
        assertEquals("EMP001", savedLog.getTargetId());
    }

    @Test
    public void testDeleteUser_SuccessByAdmin() {
        when(userRepository.findById("ADMIN01")).thenReturn(Optional.of(adminOperator));
        when(userRepository.findById("EMP001")).thenReturn(Optional.of(employeeOperator));

        userService.deleteUser("EMP001", "ADMIN01");

        verify(userRepository, times(1)).delete(employeeOperator);

        // 驗證是否寫入 AuditLog
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        assertEquals("DELETE_USER", logCaptor.getValue().getActionType());
    }

    @Test
    public void testChangePassword_SuccessByAdmin() {
        when(userRepository.findById("ADMIN01")).thenReturn(Optional.of(adminOperator));
        when(userRepository.findById("EMP001")).thenReturn(Optional.of(employeeOperator));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword("EMP001", "NewSecurePassword", "ADMIN01");

        assertEquals(hashPassword("NewSecurePassword"), employeeOperator.getPasswordHash());

        // 驗證是否寫入 AuditLog
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    // 私有輔助加密方法，用於測試比對
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
            throw new RuntimeException(ex);
        }
    }
}
