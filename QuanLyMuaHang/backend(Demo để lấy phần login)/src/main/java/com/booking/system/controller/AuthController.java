package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.AuthResponse;
import com.booking.system.dto.ForgotPasswordOtpRequest;
import com.booking.system.dto.GoogleLoginRequest;
import com.booking.system.dto.LoginRequest;
import com.booking.system.dto.LogoutRequest;
import com.booking.system.dto.RefreshTokenRequest;
import com.booking.system.dto.RegisterOtpRequest;
import com.booking.system.dto.RegisterVerifyRequest;
import com.booking.system.dto.ResetPasswordRequest;
import com.booking.system.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Đăng nhập bằng email và mật khẩu.
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.authenticate(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(ApiResponse.success(response, "Đăng nhập thành công"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, e.getMessage()));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        try {
            // Uncomment line below to bypass real Google Verification if you don't have Client ID configured yet
            // AuthResponse response = authService.authenticateDummyForDev("admin@booking.com");

            AuthResponse response = authService.authenticateWithGoogle(request.getIdToken());
            return ResponseEntity.ok(ApiResponse.success(response, "Đăng nhập thành công"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, e.getMessage()));
        }
    }

    // Gửi OTP để đăng ký tài khoản mới.
    @PostMapping("/register/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestRegisterOtp(@Valid @RequestBody RegisterOtpRequest request) {
        try {
            authService.requestRegisterOtp(request.email());
            return ResponseEntity.ok(ApiResponse.success(null, "Đã gửi mã OTP đăng ký tới email"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // Xác thực OTP và tạo tài khoản email/password.
    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<Void>> verifyRegisterOtp(@Valid @RequestBody RegisterVerifyRequest request) {
        try {
            authService.verifyRegisterOtp(request.email(), request.otp(), request.fullName(), request.password());
            return ResponseEntity.ok(ApiResponse.success(null, "Email đã được xác minh. Tài khoản đang chờ quản trị viên phê duyệt"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // Gửi OTP để đặt lại mật khẩu.
    @PostMapping("/forgot-password/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestForgotPasswordOtp(@Valid @RequestBody ForgotPasswordOtpRequest request) {
        try {
            authService.requestForgotPasswordOtp(request.email());
            return ResponseEntity.ok(ApiResponse.success(null, "Đã gửi mã OTP đặt lại mật khẩu tới email"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // Xác thực OTP và cập nhật mật khẩu mới.
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPasswordWithOtp(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPasswordWithOtp(request.email(), request.otp(), request.newPassword());
            return ResponseEntity.ok(ApiResponse.success(null, "Đổi mật khẩu thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(ApiResponse.success(response, "Refresh token thành công"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(null, "Đăng xuất thành công"));
    }
}
