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

    /**
     * 로그인 여부와 무관하게 접근 가능한 화면(공개 게시글 등)에서 "로그인했으면 그 id, 아니면 null"이 필요할 때 사용합니다.
     */
    public static Long getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return (Long) authentication.getPrincipal();
    }
}
