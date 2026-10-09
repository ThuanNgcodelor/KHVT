package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.BookingRoomRequest;
import com.booking.system.entity.BookingRoom;
import com.booking.system.entity.User;
import com.booking.system.service.BookingRoomService;
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

@RestController
@RequestMapping("/api/v1/bookings/rooms")
@RequiredArgsConstructor
public class BookingRoomController {

    private final BookingRoomService bookingRoomService;

    /**
     * API tạo mới yêu cầu đặt phòng họp.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BookingRoom>> createBooking(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BookingRoomRequest request) {
        try {
            BookingRoom booking = bookingRoomService.createBooking(request, user);
            return ResponseEntity.ok(ApiResponse.success(booking, "Tạo yêu cầu đặt phòng thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "Đã xảy ra lỗi hệ thống: " + e.getMessage()));
        }
    }

    /**
     * API lấy danh sách đặt phòng. Nếu có start/end thì chỉ trả booking giao với range calendar.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<BookingRoom>>> getAllBookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) BookingStatus status) {
        if (start == null && end == null) {
            return ResponseEntity.ok(ApiResponse.success(bookingRoomService.getAllBookings(), "Lấy danh sách đặt phòng thành công"));
        }

        if (start == null || end == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Cần truyền đủ start và end khi lọc theo khoảng ngày"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    bookingRoomService.getBookingsByDateRange(start, end, roomId, status),
                    "Lấy danh sách đặt phòng theo khoảng ngày thành công"));
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
            bookingRoomService.cancelBooking(id, request, user);
            return ResponseEntity.ok(ApiResponse.success(null, "Đã hủy lịch đặt phòng thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
