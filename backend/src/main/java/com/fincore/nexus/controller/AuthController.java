package com.fincore.nexus.controller;

import com.fincore.nexus.security.DevJwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication & Security", description = "Endpoints for generating test JWT tokens and inspecting session status")
public class AuthController {

    private final DevJwtService devJwtService;

    public AuthController(DevJwtService devJwtService) {
        this.devJwtService = devJwtService;
    }

    @PostMapping("/token")
    @Operation(summary = "Generate Development JWT Token", description = "Generates a signed JWT with role CUSTOMER, TELLER, or ADMIN for testing")
    public ResponseEntity<Map<String, Object>> generateToken(
            @RequestParam(defaultValue = "admin@fincore.bank") String username,
            @RequestParam(defaultValue = "ADMIN") String role,
            @RequestParam(required = false) UUID customerId) {

        String token = devJwtService.generateToken(username, role, customerId);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "tokenType", "Bearer",
                "username", username,
                "role", role.toUpperCase(),
                "expiresInSeconds", 86400
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Inspect Current Authenticated User", description = "Returns active identity and granted security roles")
    public ResponseEntity<Map<String, Object>> getAuthStatus(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }

        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return ResponseEntity.ok(Map.of(
                "authenticated", authentication.isAuthenticated(),
                "username", authentication.getName(),
                "roles", roles
        ));
    }
}
