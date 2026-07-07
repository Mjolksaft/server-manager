package com.example.servermanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.api-key}")
    private String apiKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(apiKeyFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    private Filter apiKeyFilter() {
        return (ServletRequest, ServletResponse, FilterChain) -> {
            HttpServletRequest req = (HttpServletRequest) ServletRequest;
            String path = req.getRequestURI();

            boolean isPublic = path.equals("/") || path.equals("/index.html")
                || path.startsWith("/app.js") || path.equals("/error")
                || path.startsWith("/ws/") || path.startsWith("/mod/")
                || req.getMethod().equals("OPTIONS");

            if (isPublic || apiKey.equals(req.getHeader("X-Api-Key"))) {
                FilterChain.doFilter(ServletRequest, ServletResponse);
            } else {
                HttpServletResponse res = (HttpServletResponse) ServletResponse;
                res.setStatus(403);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
            }
        };
    }
}
