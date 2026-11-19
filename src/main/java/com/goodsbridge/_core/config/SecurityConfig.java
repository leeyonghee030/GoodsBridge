
package com.goodsbridge._core.config;

import com.goodsbridge._core.security.JwtAuthenticationFilter;
import com.goodsbridge._core.security.JwtProvider;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
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
                // SSE(SseEmitter) 같은 비동기 응답은 완료/타임아웃 때 ASYNC 로 다시 디스패치된다.
                // 이미 인가를 통과한 요청의 후속 처리인데 STATELESS 라 인증 정보가 없어 Access Denied 가 나므로 통과시킨다.
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()

                // 💡 [필수 추가] 관리자 전용 경로는 ROLE_ADMIN만 접근 가능하도록 최상단에 배치
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // 관리자 화면과 마이페이지 화면도 로그인(관리자 화면은 ADMIN)이 있어야 연다.
                // 아래 permitAll 보다 먼저 와야 한다 (먼저 일치하는 규칙이 적용되므로, permitAll 목록에 /my/** 가 있어도 여기가 이긴다)
                .requestMatchers("/test/admin/**").hasRole("ADMIN")
                .requestMatchers("/my/**").authenticated()

                .requestMatchers(
                    "/", "/error", "/api/kakao-login",
                    "/oauth2/**", "/login/**", "/api/join", "/join", "/api/login",
                    "/css/**", "/js/**", "/img/**", "/favicon.ico", "/images/**", "/api/email/**",
                    // /shop/exchange: 가게 사장님 화면. 사장님이 아니면 컨트롤러가 /my/exchange 로 보낸다 (401 빈 화면 방지)
                    "/test/**", "/exchange/**", "/demo/**", "/my/**", "/shop/exchange",
                    // 푸터에서 연결되는 정적 안내 화면 (로그인 없이 볼 수 있다)
                    "/about", "/shop-guide", "/notice", "/faq"
                ).permitAll()

                .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()

                // 목록/상세 조회는 비로그인도 가능 (CLAUDE.md 권한 규칙)
                .requestMatchers(HttpMethod.GET, "/api/shops/**", "/api/items/**", "/api/boards/**", "/api/exchange-items/**", "/api/categories/**").permitAll()

                // 인증이 필요한 경로를 여기에 추가 (조회 외 나머지 메서드)
                .requestMatchers("/api/shops/**", "/api/items/**", "/api/boards/**", "/api/exchange-items/**","/api/replies/**")
                .authenticated()

                .requestMatchers(HttpMethod.GET, "/api/auctions", "/api/auctions/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/**").permitAll()

                // 토큰 재발급은 Access Token이 만료된 뒤에도 Refresh Token 쿠키만으로 호출할 수 있어야 한다.
                .requestMatchers(HttpMethod.POST, "/api/auth/reissue").permitAll()

                .anyRequest().authenticated()
            )

            // 로그인이 필요한 화면을 비로그인 브라우저가 열면 빈 403 대신 홈으로 보내 로그인 창을 띄운다.
            // API 요청이나 브라우저가 아닌 요청은 기존처럼 403 을 돌려준다.
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(loginRedirectEntryPoint()))

            // JWT 필터 등록
            .addFilterBefore(
                jwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class
            );



        return http.build();
    }

    private AuthenticationEntryPoint loginRedirectEntryPoint() {
        AuthenticationEntryPoint forbidden = new Http403ForbiddenEntryPoint();
        return (request, response, authException) -> {
            String accept = request.getHeader("Accept");
            boolean browserPage = "GET".equals(request.getMethod())
                && !request.getRequestURI().startsWith("/api/")
                && accept != null && accept.contains("text/html");
            if (browserPage) {
                // 헤더가 ?login=open 이면 로그인 창을 자동으로 연다
                response.sendRedirect("/?login=open");
            } else {
                forbidden.commence(request, response, authException);
            }
        };
    }
}
