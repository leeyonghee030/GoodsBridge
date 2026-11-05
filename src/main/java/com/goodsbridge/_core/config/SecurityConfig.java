
package com.goodsbridge._core.config;

import com.goodsbridge._core.security.JwtAuthenticationFilter;
import com.goodsbridge._core.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception {

        http
            // CSRF 비활성화
            .csrf(csrf -> csrf.disable())

            // 기본 로그인 방식 비활성화
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())

            // 세션 사용하지 않음
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // API 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/error",
                    "/oauth2/**",
                    "/login/**",
                    "/kakao-login",
                    "/api/auth/**",
                    "/api/shops",
                    "/api/items",
                    "/api/boards",
                    "/css/**",
                    "/js/**",
                    "/test/**",
                    "/login",
                    "/items"
                ).permitAll()

                // 목록/상세 조회는 비로그인도 가능하게 (CLAUDE.md 권한 규칙)
                .requestMatchers(HttpMethod.GET, "/api/auctions", "/api/auctions/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/**").permitAll()

                .anyRequest().authenticated()
            )

            // JWT 필터 등록
            .addFilterBefore(
                jwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
