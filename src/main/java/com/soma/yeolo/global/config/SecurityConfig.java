package com.soma.yeolo.global.config;

import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * REST + JWT 기반. 세션 미사용(STATELESS). 인증 API는 공개, 나머지는 인증 필요.
 * {@link JwtAuthenticationFilter}가 Bearer 토큰을 검증해 SecurityContext를 채우고,
 * 미인증 접근은 {@link RestAuthenticationEntryPoint}가 401 봉투로 응답한다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .oauth2Login(oauth2 -> oauth2.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // SSE/async 응답은 완료 시 컨테이너가 필터체인으로 ASYNC(에러 시 ERROR)
                        // 재디스패치를 한다. STATELESS + OncePerRequestFilter(ASYNC 미실행) 조합상
                        // 이 재디스패치에는 인증이 없어 AuthorizationFilter가 Access Denied를 던진다.
                        // 최초 REQUEST 디스패치에서 이미 인가된 내부 재디스패치이므로 허용한다.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // 로그아웃(API-AUTH-4)은 인증 필요 — 공개 인증 API보다 먼저 매칭한다.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        // 국가·도시 자동완성(API-LOC-1/2)은 명세상 "인증 필요: N"인 공개 기준 정보다.
                        // 사용자별로 달라지는 값이 없고, 로그인 전 화면에서도 지역을 훑어볼 수 있어야 한다.
                        //
                        // API-LOC-2 명세는 원래 자기모순이었다 — 기본 정보는 "인증 필요: N"인데
                        // Header 에 Authorization 이 있고 Error Code 에 401 이 있었다. **공개로
                        // 확정**했고 Notion 원본·명세 파일에도 반영돼 모순은 해소된 상태다.
                        // (경위는 docs/spec-index.md "해소된 명세 모순 — API-LOC-2 인증")
                        .requestMatchers(HttpMethod.GET, "/api/locations/**").permitAll()
                        // k8s readiness/liveness probe 경로는 인증 없이 허용.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * {@link JwtAuthenticationFilter}는 SecurityFilterChain 안에서만 실행되도록,
     * 서블릿 컨테이너의 자동 필터 등록은 비활성화한다(이중 등록 방지).
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
