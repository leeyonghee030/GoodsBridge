
package com.goodsbridge._core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Authorization 헤더 가져오기
        String authorization = request.getHeader("Authorization");

        // 2. Bearer 토큰 확인
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);

        try {
            // 3. Access Token 검증
            Claims claims = jwtProvider.validateAccessToken(token);

            // 4. 사용자 정보 추출
            Long userId = jwtProvider.getUserId(claims);
            String role = jwtProvider.getRole(claims);

            // 5. 권한 생성
            SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_" + role);

            // 6. 인증 객체 생성
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(authority)
                );

            // 7. SecurityContext에 인증 정보 저장
            SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException e) {
            // 유효하지 않은 토큰이면 인증 정보 제거
            SecurityContextHolder.clearContext();
        }

        // 8. 다음 필터 실행
        filterChain.doFilter(request, response);
    }
}
