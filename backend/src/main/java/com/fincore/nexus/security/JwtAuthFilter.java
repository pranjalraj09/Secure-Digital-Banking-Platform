package com.fincore.nexus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final DevJwtService devJwtService;
    private final boolean devAuthEnabled;

    public JwtAuthFilter(
            DevJwtService devJwtService,
            @Value("${banking.dev-auth.enabled:true}") boolean devAuthEnabled) {
        this.devJwtService = devJwtService;
        this.devAuthEnabled = devAuthEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && devJwtService.validateToken(token)) {
            var authentication = devJwtService.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else if (devAuthEnabled && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Optional development header support for local testing convenience:
            // e.g. "X-Dev-Role: TELLER", "X-Dev-User: teller@fincore.com"
            String devRole = request.getHeader("X-Dev-Role");
            String devUser = request.getHeader("X-Dev-User");
            if (StringUtils.hasText(devRole)) {
                String role = devRole.startsWith("ROLE_") ? devRole : "ROLE_" + devRole;
                String user = StringUtils.hasText(devUser) ? devUser : "dev-user";
                var auth = new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority(role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
