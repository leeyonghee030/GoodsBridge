
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
                    "/", "/error", "/api/kakao-login",
                    "/oauth2/**", "/login/**", "/api/join", "/api/login", "/api/logout",
                    "/css/**", "/js/**", "/favicon.ico"
                ).permitAll()

                .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()

                // 목록/상세 조회는 비로그인도 가능 (CLAUDE.md 권한 규칙)
                .requestMatchers(HttpMethod.GET, "/api/shops/**", "/api/items/**", "/api/boards/**").permitAll()

                // 인증이 필요한 경로를 여기에 추가 (조회 외 나머지 메서드)
                .requestMatchers("/api/shops/**", "/api/items/**", "/api/boards/**")
                .authenticated()

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
