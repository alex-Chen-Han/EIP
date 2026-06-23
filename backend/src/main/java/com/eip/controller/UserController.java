package com.eip.controller;

import com.eip.entity.Department;
import com.eip.entity.User;
import com.eip.repository.DepartmentRepository;
import com.eip.repository.UserRepository;
import com.eip.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.eip.util.UserUtils;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        id = UserUtils.normalizeUserId(id);
        User user = userService.getUserById(id);
        if (user != null) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> createUser(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestBody UserRequest request) {
        try {
            operatorId = UserUtils.normalizeUserId(operatorId);
            if (request.getUserId() != null) {
                request.setUserId(UserUtils.normalizeUserId(request.getUserId()));
            }
            if (request.getManagerId() != null) {
                request.setManagerId(UserUtils.normalizeUserId(request.getManagerId()));
            }

            Department dept = departmentRepository.findById(request.getDeptId())
                    .orElseThrow(() -> new IllegalArgumentException("部門不存在：" + request.getDeptId()));

            User manager = null;
            if (request.getManagerId() != null && !request.getManagerId().trim().isEmpty()) {
                manager = userRepository.findById(request.getManagerId())
                        .orElseThrow(() -> new IllegalArgumentException("主管帳號不存在：" + request.getManagerId()));
            }

            User user = User.builder()
                    .userId(request.getUserId())
                    .realName(request.getRealName())
                    .idNumber(request.getIdNumber())
                    .bankAccount(request.getBankAccount())
                    .department(dept)
                    .position(request.getPosition())
                    .role(request.getRole())
                    .manager(manager)
                    .build();

            User created = userService.createUser(user, request.getPassword(), operatorId);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String operatorId,
            @RequestBody UserRequest request) {
        try {
            id = UserUtils.normalizeUserId(id);
            operatorId = UserUtils.normalizeUserId(operatorId);
            if (request.getManagerId() != null) {
                request.setManagerId(UserUtils.normalizeUserId(request.getManagerId()));
            }

            Department dept = departmentRepository.findById(request.getDeptId())
                    .orElseThrow(() -> new IllegalArgumentException("部門不存在：" + request.getDeptId()));

            User manager = null;
            if (request.getManagerId() != null && !request.getManagerId().trim().isEmpty()) {
                manager = userRepository.findById(request.getManagerId())
                        .orElseThrow(() -> new IllegalArgumentException("主管帳號不存在：" + request.getManagerId()));
            }

            User user = User.builder()
                    .userId(id)
                    .realName(request.getRealName())
                    .idNumber(request.getIdNumber())
                    .bankAccount(request.getBankAccount())
                    .department(dept)
                    .position(request.getPosition())
                    .role(request.getRole())
                    .manager(manager)
                    .build();

            User updated = userService.updateUser(user, operatorId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<?> changePassword(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String operatorId,
            @RequestBody Map<String, String> body) {
        try {
            id = UserUtils.normalizeUserId(id);
            operatorId = UserUtils.normalizeUserId(operatorId);
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("新密碼不可為空");
            }
            userService.changePassword(id, newPassword, operatorId);
            return ResponseEntity.ok().body("密碼修改成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String operatorId) {
        try {
            id = UserUtils.normalizeUserId(id);
            operatorId = UserUtils.normalizeUserId(operatorId);
            userService.deleteUser(id, operatorId);
            return ResponseEntity.ok().body("帳號刪除成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/departments")
    public ResponseEntity<List<Department>> getAllDepartments() {
        return ResponseEntity.ok(userService.getAllDepartments());
    }

    @Data
    public static class UserRequest {
        private String userId;
        private String password;
        private String realName;
        private String idNumber;
        private String bankAccount;
        private String deptId;
        private String position;
        private String role;
        private String managerId;
    }
}
