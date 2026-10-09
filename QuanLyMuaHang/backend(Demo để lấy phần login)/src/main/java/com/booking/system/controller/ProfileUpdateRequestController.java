package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.ProfileUpdateRequestCreateRequest;
import com.booking.system.dto.ProfileUpdateRequestResponse;
import com.booking.system.dto.ProfileUpdateReviewRequest;
import com.booking.system.entity.User;
import com.booking.system.enums.ProfileUpdateRequestStatus;
import com.booking.system.repository.ProfileUpdateRequestRepository;
import com.booking.system.service.ProfileUpdateRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profile-requests")
@RequiredArgsConstructor
public class ProfileUpdateRequestController {

    private final ProfileUpdateRequestService profileUpdateRequestService;
    private final ProfileUpdateRequestRepository profileUpdateRequestRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<ProfileUpdateRequestResponse>> submit(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ProfileUpdateRequestCreateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    profileUpdateRequestService.submitRequest(requireUser(user).getId(), request),
                    "Đã gửi yêu cầu cập nhật hồ sơ"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<ProfileUpdateRequestResponse>>> getPending(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            requireAdmin(user);
            Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
            return ResponseEntity.ok(ApiResponse.success(
                    profileUpdateRequestService.getPendingRequests(pageable).getContent(),
                    "Lấy danh sách yêu cầu chờ duyệt thành công"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ApiResponse.error(403, e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ProfileUpdateRequestResponse>>> getMyRequests(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                profileUpdateRequestService.getMyRequests(requireUser(user).getId()),
                "Lấy danh sách yêu cầu của bạn thành công"
        ));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestResponse>> approve(
            @AuthenticationPrincipal User approver,
            @PathVariable String id) {
        try {
            requireAdmin(approver);
            return ResponseEntity.ok(ApiResponse.success(
                    profileUpdateRequestService.approve(id, approver.getId()),
                    "Phê duyệt cập nhật hồ sơ thành công"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestResponse>> reject(
            @AuthenticationPrincipal User approver,
            @PathVariable String id,
            @RequestBody ProfileUpdateReviewRequest request) {
        try {
            requireAdmin(approver);
            return ResponseEntity.ok(ApiResponse.success(
                    profileUpdateRequestService.reject(id, approver.getId(), request.getReason()),
                    "Từ chối cập nhật hồ sơ thành công"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<ProfileUpdateRequestResponse>> getLatestForCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                profileUpdateRequestRepository.findTopByRequesterIdOrderByRequestedAtDesc(requireUser(user).getId())
                        .map(ProfileUpdateRequestResponse::from)
                        .orElse(null),
                "Lấy yêu cầu gần nhất thành công"
        ));
    }

    private User requireUser(User user) {
        if (user == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        return user;
    }

    private void requireAdmin(User user) {
        User current = requireUser(user);
        if (current.getRole() == null || !"ADMIN".equals(current.getRole().name())) {
            throw new RuntimeException("Chỉ quản trị viên mới có quyền thực hiện chức năng này");
        }
    }
}
