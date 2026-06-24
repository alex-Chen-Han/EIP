package com.eip.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        
        // 排除登入路徑與 OPTIONS 預檢請求
        if (path.startsWith("/api/auth/login") || httpRequest.getMethod().equalsIgnoreCase("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                DecodedJWT decodedJWT = JwtUtils.verifyToken(token);
                String userId = decodedJWT.getSubject();

                // 檢查是否需要刷新 Token（滑動過期）
                if (JwtUtils.shouldRefreshToken(decodedJWT)) {
                    String role = decodedJWT.getClaim("role").asString();
                    String newToken = JwtUtils.generateToken(userId, role);
                    // 塞入新的 Token
                    httpResponse.setHeader("Authorization-New", newToken);
                    // 在跨域 CORS 環境下，必須顯式暴露該自訂 Header，前端才能讀取到
                    httpResponse.setHeader("Access-Control-Expose-Headers", "Authorization-New");
                    log.info("Token 剩餘時間少於 15 分鐘，已自動刷新並隨 Response Header (Authorization-New) 傳回給使用者 {}", userId);
                }

                // 使用 Wrapper 重寫 X-User-Id
                HeaderMapRequestWrapper requestWrapper = new HeaderMapRequestWrapper(httpRequest);
                requestWrapper.addHeader("X-User-Id", userId);

                chain.doFilter(requestWrapper, response);
                return;
            } catch (Exception e) {
                log.warn("JWT 驗證失敗 (路徑: {}): {}", path, e.getMessage());
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("text/plain;charset=UTF-8");
                httpResponse.getWriter().write("認證無效或已過期");
                return;
            }
        }

        log.warn("無 Authorization Header，拒絕存取路徑: {}", path);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setContentType("text/plain;charset=UTF-8");
        httpResponse.getWriter().write("請提供認證 Token");
    }
}
