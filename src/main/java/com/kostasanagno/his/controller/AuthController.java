package com.kostasanagno.his.controller;

import com.kostasanagno.his.audit.Audited;
import com.kostasanagno.his.dto.LoginRequest;
import com.kostasanagno.his.dto.LoginResponse;
import com.kostasanagno.his.dto.RegisterRequest;
import com.kostasanagno.his.dto.UserResponse;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.AuthService;
import com.kostasanagno.his.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange credentials for a JWT access token (audited by AuthenticationAuditListener)")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Audited(resource = AuditResource.USER, action = AuditAction.CREATE)
    @Operation(summary = "Create a user (ADMIN only)")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }
}
