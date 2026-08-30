package com.recruitment.controller;

import com.recruitment.dto.ApiResponse;
import com.recruitment.dto.AuthResponse;
import com.recruitment.dto.ForgotPasswordRequest;
import com.recruitment.dto.LoginRequest;
import com.recruitment.dto.ResetPasswordRequest;
import com.recruitment.dto.SignupRequest;
import com.recruitment.model.OrgMembership;
import com.recruitment.service.AuthService;
import com.recruitment.service.PasswordResetService;
import com.recruitment.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(
                "If that email is registered, a password reset link has been sent.", "Check your inbox"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successfully. You can now sign in with your new password.", "Password updated"));
    }

    @GetMapping("/me/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthResponse>> getMe(@PathVariable Long userId) {
        AuthResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", response));
    }

    @GetMapping("/roles/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OrgMembership>>> getRoles(@PathVariable Long userId) {
        List<OrgMembership> memberships = authService.getRolesForUser(userId);
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", memberships));
    }

    @GetMapping("/super-admin/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isSuperAdmin(@PathVariable Long userId) {
        boolean isSuperAdmin = authService.isSuperAdmin(userId);
        return ResponseEntity.ok(ApiResponse.success("Check completed", Map.of("isSuperAdmin", isSuperAdmin)));
    }
}
