package com.booking.system.service;

import com.booking.system.dto.BookingRoomRequest;
import com.booking.system.entity.BookingRoom;
import com.booking.system.entity.Room;
import com.booking.system.entity.User;
import com.booking.system.enums.BookingStatus;
import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.RoomStatus;
import com.booking.system.event.NotificationEvent;
import com.booking.system.repository.BookingRoomRepository;
import com.booking.system.repository.RoomRepository;
import com.booking.system.repository.UserRepository;
import com.booking.system.enums.NotificationType;
import com.booking.system.enums.RoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingRoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final BookingRoomRepository bookingRoomRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Hàm đặt phòng có kiểm tra trùng lịch và khóa DB.
     * @param request dữ liệu đặt phòng
     * @return BookingRoom đã tạo
     */
    @Transactional
    public BookingRoom createBooking(BookingRoomRequest request, User requesterPrincipal) {
        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().isEqual(request.getEndTime())) {
            throw new RuntimeException("Thời gian bắt đầu phải trước thời gian kết thúc.");
        }

        // 1. Lấy Room và khóa dòng này lại (Pessimistic Write)
        // Bất kỳ thread nào khác muốn đặt phòng này cũng phải chờ thread hiện tại hoàn thành.
        Room room = roomRepository.findByIdWithLock(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng"));

        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new RuntimeException("Phòng đang không hoạt động (Bảo trì hoặc Ngưng sử dụng)");
        }

        User requester = userRepository.findById(requirePrincipalId(requesterPrincipal))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người đặt"));

        // 2. Kiểm tra trùng lịch (Dựa trên những booking đã duyệt hoặc đang chờ duyệt)
        long overlaps = bookingRoomRepository.countOverlappingBookings(
                room.getId(), request.getStartTime(), request.getEndTime());

        if (overlaps > 0) {
            throw new RuntimeException("Phòng đã có người đặt trong khoảng thời gian này.");
        }

        // 3. Tạo mới Booking
        BookingRoom booking = new BookingRoom();
        booking.setRoom(room);
        booking.setRequester(requester);
        booking.setTitle(request.getTitle());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setAttendeeCount(request.getAttendeeCount());
        booking.setNote(request.getNote());
        booking.setStatus(BookingStatus.PENDING); // Chờ duyệt

        BookingRoom saved = bookingRoomRepository.save(booking);
        
        // Thông báo cho người đặt
        eventPublisher.publishEvent(new NotificationEvent(
                requester.getId(),
                null,
                NotificationType.BOOKING_CREATED,
                "Tạo yêu cầu đặt phòng thành công",
                "Yêu cầu đặt phòng '" + saved.getTitle() + "' đã được gửi và đang chờ duyệt.",
                "/rooms",
                "BOOKING_ROOM",
                saved.getId(),
                NotificationPriority.NORMAL,
                null
        ));
            
        // Thông báo cho Admin
        java.util.List<User> admins = userRepository.findByRole(com.booking.system.enums.RoleEnum.ADMIN);
        for (User admin : admins) {
            if (admin.getId().equals(requester.getId())) {
                continue;
            }
            eventPublisher.publishEvent(new NotificationEvent(
                    admin.getId(),
                    requester.getId(),
                    NotificationType.BOOKING_PENDING_APPROVAL,
                    "Yêu cầu đặt phòng mới (Cần duyệt)",
                    requester.getFullName() + " vừa tạo một yêu cầu đặt phòng mới ('" + saved.getTitle() + "').",
                    "/admin/approvals",
                    "BOOKING_ROOM",
                    saved.getId(),
                    NotificationPriority.HIGH,
                    new NotificationEvent.EmailInstruction(
                            NotificationEvent.EmailType.BOOKING_CREATED_TO_ADMIN,
                            "phòng",
                            requester.getFullName(),
                            saved.getTitle(),
                            null,
                            new NotificationEvent.BookingEmailDetails(
                                    saved.getRoom().getName(), saved.getRoom().getLocation(), null, null,
                                    saved.getStartTime(), saved.getEndTime())
                    )
            ));
        }
            
        return saved;
    }

    public java.util.List<BookingRoom> getAllBookings() {
        return bookingRoomRepository.findAll();
    }

    /**
     * Lấy lịch đặt phòng theo khoảng ngày calendar đang hiển thị.
     */
    public List<BookingRoom> getBookingsByDateRange(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String roomId,
            BookingStatus status) {
        if (startTime.isAfter(endTime) || startTime.isEqual(endTime)) {
            throw new RuntimeException("Thời gian bắt đầu phải trước thời gian kết thúc.");
        }

        return bookingRoomRepository.findByDateRange(startTime, endTime, roomId, status);
    }

    @Transactional
    public void cancelBooking(String bookingId, com.booking.system.dto.CancelRequest request, User cancellerPrincipal) {
        User canceller = userRepository.findById(requirePrincipalId(cancellerPrincipal))
                .orElseThrow(() -> new AccessDeniedException("Người hủy không tồn tại"));
        BookingRoom booking = bookingRoomRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch đặt phòng"));

        requireCancellationPermission(canceller, booking.getRequester());
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new RuntimeException("Lịch đặt phòng này đã bị hủy trước đó");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.APPROVED) {
            throw new RuntimeException("Chỉ có thể hủy lịch đang chờ duyệt hoặc đã được duyệt");
        }
        String reason = normalizeCancellationReason(request.getReason());
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(reason);
        booking.setCancelledBy(canceller);
        booking.setCancelledAt(LocalDateTime.now());
        
        bookingRoomRepository.save(booking);

        // Notify if the canceller is not the requester (e.g. Admin cancelled it)
        if (!canceller.getId().equals(booking.getRequester().getId())) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getRequester().getId(),
                    canceller.getId(),
                    NotificationType.BOOKING_CANCELLED,
                    "Lịch đặt phòng đã bị hủy",
                    buildCancellationMessage("Lịch đặt phòng '" + booking.getTitle() + "' đã bị hủy.", reason),
                    "/rooms",
                    "BOOKING_ROOM",
                    booking.getId(),
                    NotificationPriority.HIGH,
                    null
            ));
        }
    }

    private String requirePrincipalId(User principal) {
        if (principal == null || principal.getId() == null || principal.getId().isBlank()) {
            throw new AccessDeniedException("Chưa đăng nhập");
        }
        return principal.getId();
    }

    private void requireCancellationPermission(User canceller, User requester) {
        if (canceller.getRole() != RoleEnum.ADMIN && !canceller.getId().equals(requester.getId())) {
            throw new AccessDeniedException("Bạn không có quyền hủy booking này");
        }
    }

    private String normalizeCancellationReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private String buildCancellationMessage(String message, String reason) {
        return reason == null ? message : message + " Lý do: " + reason;
    }
}
