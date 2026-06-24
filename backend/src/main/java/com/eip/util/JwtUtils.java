package com.eip.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.util.Date;

public class JwtUtils {
    // 固定的密鑰。在實際生產環境中應載入自設定檔，此處供 MVP 展示使用
    private static final String SECRET_KEY = "YourSuperSecretKeyForEipSystemJwtAuthenticationSign";
    // Token 有效期為 30 分鐘
    private static final long EXPIRATION_TIME = 30 * 60 * 1000;

    public static boolean shouldRefreshToken(DecodedJWT decodedJWT) {
        Date expiresAt = decodedJWT.getExpiresAt();
        if (expiresAt == null) {
            return false;
        }
        long remainingTime = expiresAt.getTime() - System.currentTimeMillis();
        // 剩餘時間小於 15 分鐘（15 * 60 * 1000 毫秒）時，回傳 true
        return remainingTime > 0 && remainingTime < (15 * 60 * 1000);
    }

    public static String generateToken(String userId, String role) {
        return JWT.create()
                .withSubject(userId)
                .withClaim("role", role)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .withIssuer("EIP-Backend")
                .sign(Algorithm.HMAC256(SECRET_KEY));
    }

    public static DecodedJWT verifyToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer("EIP-Backend")
                .build();
        return verifier.verify(token);
    }
}
