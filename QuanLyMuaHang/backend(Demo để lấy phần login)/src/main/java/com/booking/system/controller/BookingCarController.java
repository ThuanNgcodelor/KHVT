package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.BookingCarRequest;
import com.booking.system.entity.BookingCar;
import com.booking.system.entity.User;
import com.booking.system.service.BookingCarService;
import com.booking.system.enums.BookingStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings/cars")
@RequiredArgsConstructor
public class BookingCarController {

    private final BookingCarService bookingCarService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingCar>> createBooking(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BookingCarRequest request) {
        try {
            BookingCar booking = bookingCarService.createBooking(request, user);
            return ResponseEntity.ok(ApiResponse.success(booking, "Tạo yêu cầu đặt xe thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "Đã xảy ra lỗi hệ thống: " + e.getMessage()));
        }
    }
    
    /**
     * API lấy danh sách đặt xe. Nếu có start/end thì chỉ trả booking giao với range calendar.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingCar>>> getAllBookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String vehicleId,
            @RequestParam(required = false) BookingStatus status) {
        if (start == null && end == null) {
            return ResponseEntity.ok(ApiResponse.success(bookingCarService.getAllBookings(), "Lấy danh sách đặt xe thành công"));
        }

        if (start == null || end == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Cần truyền đủ start và end khi lọc theo khoảng ngày"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    bookingCarService.getBookingsByDateRange(start, end, vehicleId, status),
                    "Lấy danh sách đặt xe theo khoảng ngày thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @Valid @RequestBody com.booking.system.dto.CancelRequest request) {
        try {
            bookingCarService.cancelBooking(id, request, user);
            return ResponseEntity.ok(ApiResponse.success(null, "Đã hủy lịch đặt xe thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
