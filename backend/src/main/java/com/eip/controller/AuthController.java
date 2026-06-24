package com.eip.controller;

import com.eip.entity.Department;
import com.eip.entity.User;
import com.eip.service.UserService;
import com.eip.util.JwtUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userService.authenticate(request.getUserId(), request.getPassword());
        if (user != null) {
            String token = JwtUtils.generateToken(user.getUserId(), user.getRole());
            LoginResponse response = new LoginResponse(
                    token,
                    user.getUserId(),
                    user.getRealName(),
                    user.getRole(),
                    user.getPosition(),
                    user.getIdNumber(),
                    user.getBankAccount(),
                    user.getDepartment()
            );
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("帳號或密碼錯誤");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("X-User-Id") String userId) {
        try {
            User user = userService.getUserById(userId);
            if (user != null) {
                String newToken = JwtUtils.generateToken(user.getUserId(), user.getRole());
                return ResponseEntity.ok(java.util.Map.of("token", newToken));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("使用者不存在");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @Data
    public static class LoginRequest {
        private String userId;
        private String password;
    }

    @Data
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private String userId;
        private String realName;
        private String role;
        private String position;
        private String idNumber;
        private String bankAccount;
        private Department department;
    }
}
