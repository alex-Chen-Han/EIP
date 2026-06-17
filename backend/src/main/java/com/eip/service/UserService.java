package com.eip.service;

import com.eip.entity.Department;
import com.eip.entity.User;

import java.util.List;

public interface UserService {
    User authenticate(String userId, String rawPassword);
    User createUser(User user, String rawPassword, String operatorId);
    User updateUser(User user, String operatorId);
    void changePassword(String userId, String newPassword, String operatorId);
    void deleteUser(String userId, String operatorId);
    User getUserById(String userId);
    List<User> getAllUsers();
    List<User> getUsersByDept(String deptId);
    List<Department> getAllDepartments();
}
