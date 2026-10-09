package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.ApprovalRequest;
import com.booking.system.dto.ApprovalStepResponse;
import com.booking.system.entity.User;
import com.booking.system.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import com.booking.system.dto.ApprovalHistoryResponse;
import com.booking.system.enums.BookingStatus;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<ApprovalHistoryResponse>>> getHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "desc") String direction) {
        try {
            PageRequest pageable = PageRequest.of(
                    Math.max(page, 0),
                    Math.min(Math.max(size, 1), 50));
            LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
            LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
            return ResponseEntity.ok(ApiResponse.success(
                    approvalService.getHistory(user, type, status, keyword, fromTime, toTime,
                            "asc".equalsIgnoreCase(direction), pageable),
                    "Lấy lịch sử xử lý thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/rooms/{id}/steps")
    public ResponseEntity<ApiResponse<List<ApprovalStepResponse>>> getRoomApprovalSteps(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                approvalService.getRoomApprovalSteps(id),
                "Lấy lịch sử duyệt đặt phòng thành công"));
    }

    @GetMapping("/cars/{id}/steps")
    public ResponseEntity<ApiResponse<List<ApprovalStepResponse>>> getCarApprovalSteps(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                approvalService.getCarApprovalSteps(id),
                "Lấy lịch sử duyệt đặt xe thành công"));
    }

    @PostMapping("/rooms/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveRoom(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @Valid @RequestBody ApprovalRequest request) {
        try {
            approvalService.approveRoom(id, request, user);
            return ResponseEntity.ok(ApiResponse.success(null, "Phê duyệt đặt phòng thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/rooms/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectRoom(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @Valid @RequestBody ApprovalRequest request) {
        try {
            approvalService.rejectRoom(id, request, user);
            return ResponseEntity.ok(ApiResponse.success(null, "Từ chối đặt phòng thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/cars/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveCar(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @Valid @RequestBody ApprovalRequest request) {
        try {
            approvalService.approveCar(id, request, user);
            return ResponseEntity.ok(ApiResponse.success(null, "Phê duyệt đặt xe thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/cars/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectCar(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @Valid @RequestBody ApprovalRequest request) {
        try {
            approvalService.rejectCar(id, request, user);
            return ResponseEntity.ok(ApiResponse.success(null, "Từ chối đặt xe thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
