package com.booking.system.service;

import com.booking.system.dto.ApprovalRequest;
import com.booking.system.dto.ApprovalStepResponse;
import com.booking.system.dto.ApprovalHistoryResponse;
import com.booking.system.entity.ApprovalStep;
import com.booking.system.entity.BookingCar;
import com.booking.system.entity.BookingRoom;
import com.booking.system.entity.User;
import com.booking.system.enums.ApprovalStatus;
import com.booking.system.enums.BookingStatus;
import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.NotificationType;
import com.booking.system.enums.RoleEnum;
import com.booking.system.event.NotificationEvent;
import com.booking.system.repository.ApprovalStepRepository;
import com.booking.system.repository.BookingCarRepository;
import com.booking.system.repository.BookingRoomRepository;
import com.booking.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final BookingRoomRepository bookingRoomRepository;
    private final BookingCarRepository bookingCarRepository;
    private final UserRepository userRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<ApprovalHistoryResponse> getHistory(
            User principal,
            String type,
            BookingStatus status,
            String keyword,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            boolean ascending,
            Pageable pageable) {
        requireApproverId(principal);
        String normalizedType = normalizeType(type);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return approvalStepRepository.findHistory(
                        normalizedType, status, normalizedKeyword, fromTime, toTime, ascending, pageable)
                .map(ApprovalHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ApprovalStepResponse> getRoomApprovalSteps(String bookingId) {
        return approvalStepRepository.findByBookingRoomIdOrderByActedAtDesc(bookingId)
                .stream()
                .map(ApprovalStepResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalStepResponse> getCarApprovalSteps(String bookingId) {
        return approvalStepRepository.findByBookingCarIdOrderByActedAtDesc(bookingId)
                .stream()
                .map(ApprovalStepResponse::from)
                .toList();
    }

    @Transactional
    public void approveRoom(String bookingId, ApprovalRequest request, User approverPrincipal) {
        BookingRoom booking = bookingRoomRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch đặt phòng"));
        User approver = userRepository.findById(requireApproverId(approverPrincipal))
                .orElseThrow(() -> new RuntimeException("Người duyệt không tồn tại"));

        booking.setStatus(BookingStatus.APPROVED);
        bookingRoomRepository.save(booking);

        saveApprovalStep(approver, booking, null, ApprovalStatus.APPROVED, request.getEffectiveReason());
        
        if (!approver.getId().equals(booking.getRequester().getId())) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getRequester().getId(),
                    approver.getId(),
                    NotificationType.BOOKING_APPROVED,
                    "Yêu cầu đặt phòng đã được duyệt",
                    "Lịch đặt phòng '" + booking.getTitle() + "' đã được duyệt.",
                    "/rooms",
                    "BOOKING_ROOM",
                    booking.getId(),
                    NotificationPriority.NORMAL,
                    new NotificationEvent.EmailInstruction(
                            NotificationEvent.EmailType.BOOKING_APPROVED,
                            "phòng",
                            null,
                            booking.getTitle(),
                            null,
                            roomDetails(booking)
                    )
            ));
        }
    }

    @Transactional
    public void rejectRoom(String bookingId, ApprovalRequest request, User approverPrincipal) {
        String approverId = requireAdminId(approverPrincipal);
        BookingRoom booking = bookingRoomRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch đặt phòng"));
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Người duyệt không tồn tại"));
        String reason = normalizeReason(request.getEffectiveReason());

        booking.setStatus(BookingStatus.REJECTED);
        bookingRoomRepository.save(booking);

        saveApprovalStep(approver, booking, null, ApprovalStatus.REJECTED, reason);
        
        if (!approver.getId().equals(booking.getRequester().getId())) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getRequester().getId(),
                    approver.getId(),
                    NotificationType.BOOKING_REJECTED,
                    "Yêu cầu đặt phòng bị từ chối",
                    buildRejectionMessage("Lịch đặt phòng '" + booking.getTitle() + "' bị từ chối.", reason),
                    "/rooms",
                    "BOOKING_ROOM",
                    booking.getId(),
                    NotificationPriority.HIGH,
                    new NotificationEvent.EmailInstruction(
                            NotificationEvent.EmailType.BOOKING_REJECTED,
                            "phòng",
                            null,
                            booking.getTitle(),
                            reason,
                            roomDetails(booking)
                    )
            ));
        }
    }

    @Transactional
    public void approveCar(String bookingId, ApprovalRequest request, User approverPrincipal) {
        BookingCar booking = bookingCarRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch đặt xe"));
        User approver = userRepository.findById(requireApproverId(approverPrincipal))
                .orElseThrow(() -> new RuntimeException("Người duyệt không tồn tại"));

        booking.setStatus(BookingStatus.APPROVED);
        bookingCarRepository.save(booking);

        saveApprovalStep(approver, null, booking, ApprovalStatus.APPROVED, request.getEffectiveReason());
        
        if (!approver.getId().equals(booking.getRequester().getId())) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getRequester().getId(),
                    approver.getId(),
                    NotificationType.BOOKING_APPROVED,
                    "Yêu cầu đặt xe đã được duyệt",
                    "Lịch đặt xe từ '" + booking.getDeparture() + "' đi '" + booking.getDestination() + "' đã được duyệt.",
                    "/cars",
                    "BOOKING_CAR",
                    booking.getId(),
                    NotificationPriority.NORMAL,
                    new NotificationEvent.EmailInstruction(
                            NotificationEvent.EmailType.BOOKING_APPROVED,
                            "xe",
                            null,
                            booking.getDeparture() + " - " + booking.getDestination(),
                            null,
                            carDetails(booking)
                    )
            ));
        }
    }

    @Transactional
    public void rejectCar(String bookingId, ApprovalRequest request, User approverPrincipal) {
        String approverId = requireAdminId(approverPrincipal);
        BookingCar booking = bookingCarRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch đặt xe"));
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Người duyệt không tồn tại"));
        String reason = normalizeReason(request.getEffectiveReason());

        booking.setStatus(BookingStatus.REJECTED);
        bookingCarRepository.save(booking);

        saveApprovalStep(approver, null, booking, ApprovalStatus.REJECTED, reason);
        
        if (!approver.getId().equals(booking.getRequester().getId())) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getRequester().getId(),
                    approver.getId(),
                    NotificationType.BOOKING_REJECTED,
                    "Yêu cầu đặt xe bị từ chối",
                    buildRejectionMessage("Lịch đặt xe từ '" + booking.getDeparture() + "' đi '" + booking.getDestination() + "' bị từ chối.", reason),
                    "/cars",
                    "BOOKING_CAR",
                    booking.getId(),
                    NotificationPriority.HIGH,
                    new NotificationEvent.EmailInstruction(
                            NotificationEvent.EmailType.BOOKING_REJECTED,
                            "xe",
                            null,
                            booking.getDeparture() + " - " + booking.getDestination(),
                            reason,
                            carDetails(booking)
                    )
            ));
        }
    }

    private String requireApproverId(User principal) {
        if (principal == null || principal.getId() == null || principal.getId().isBlank()) {
            throw new AccessDeniedException("Unauthenticated");
        }
        if (principal.getRole() != RoleEnum.ADMIN && principal.getRole() != RoleEnum.MANAGER) {
            throw new AccessDeniedException("Only ADMIN or MANAGER can approve requests");
        }
        return principal.getId();
    }

    private String requireAdminId(User principal) {
        if (principal == null || principal.getId() == null || principal.getId().isBlank()) {
            throw new AccessDeniedException("Unauthenticated");
        }
        if (principal.getRole() != RoleEnum.ADMIN) {
            throw new AccessDeniedException("Only ADMIN can reject requests");
        }
        return principal.getId();
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) return null;
        String normalized = type.trim().toUpperCase();
        if (!"ROOM".equals(normalized) && !"CAR".equals(normalized)) {
            throw new IllegalArgumentException("Loại yêu cầu chỉ nhận ROOM, CAR hoặc ALL");
        }
        return normalized;
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private String buildRejectionMessage(String message, String reason) {
        return reason == null ? message : message + " Lý do: " + reason;
    }

    private NotificationEvent.BookingEmailDetails roomDetails(BookingRoom booking) {
        return new NotificationEvent.BookingEmailDetails(
                booking.getRoom() == null ? null : booking.getRoom().getName(),
                booking.getRoom() == null ? null : booking.getRoom().getLocation(), null, null,
                booking.getStartTime(), booking.getEndTime());
    }

    private NotificationEvent.BookingEmailDetails carDetails(BookingCar booking) {
        return new NotificationEvent.BookingEmailDetails(
                booking.getVehicle() == null ? null : booking.getVehicle().getLicensePlate(), null,
                booking.getDeparture(), booking.getDestination(),
                booking.getStartTime(), booking.getEndTime());
    }

    private void saveApprovalStep(User approver, BookingRoom room, BookingCar car, ApprovalStatus status, String reason) {
        ApprovalStep step = new ApprovalStep();
        step.setApprover(approver);
        step.setBookingRoom(room);
        step.setBookingCar(car);
        step.setStatus(status);
        step.setReason(reason);
        step.setActedAt(LocalDateTime.now());
        approvalStepRepository.save(step);
    }
}
