package com.eip.util;

public class UserUtils {
    public static String normalizeUserId(String userId) {
        return userId != null ? userId.trim().toUpperCase() : null;
    }
}
