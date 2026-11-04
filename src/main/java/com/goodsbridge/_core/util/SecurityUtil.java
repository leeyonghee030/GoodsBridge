package com.goodsbridge._core.util;

import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge._core.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * #15의 JWT 인증 필터가 인증 성공 시 Authentication의 principal에 User.id(Long)를 세팅하는 것을 전제로 합니다.
     * #15가 완성되기 전까지는 인증된 요청이 없으므로 항상 UnauthorizedException이 던져집니다.
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("로그인이 필요한 서비스입니다.");
        }
        return (Long) authentication.getPrincipal();
    }
}
