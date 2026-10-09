package com.booking.system.service;

import com.booking.system.dto.ApprovalRequest;
import com.booking.system.entity.ApprovalStep;
import com.booking.system.entity.BookingCar;
import com.booking.system.entity.BookingRoom;
import com.booking.system.entity.User;
import com.booking.system.enums.ApprovalStatus;
import com.booking.system.enums.BookingStatus;
import com.booking.system.enums.NotificationType;
import com.booking.system.enums.RoleEnum;
import com.booking.system.event.NotificationEvent;
import com.booking.system.repository.ApprovalStepRepository;
import com.booking.system.repository.BookingCarRepository;
import com.booking.system.repository.BookingRoomRepository;
import com.booking.system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private BookingRoomRepository bookingRoomRepository;

    @Mock
    private BookingCarRepository bookingCarRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApprovalStepRepository approvalStepRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ApprovalService approvalService;

    @Test
    void approveRoomUsesAuthenticatedApproverAndPublishesNotificationForRequester() {
        User requester = user("user-1", "Nhan vien", RoleEnum.EMPLOYEE);
        User approver = user("admin-1", "Quan ly", RoleEnum.ADMIN);
        BookingRoom booking = bookingRoom("booking-room-1", requester);
        ApprovalRequest request = new ApprovalRequest();
        request.setApproverId("spoofed-admin-id");
        request.setReason("OK");

        when(bookingRoomRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findById(approver.getId())).thenReturn(Optional.of(approver));
        when(bookingRoomRepository.save(booking)).thenReturn(booking);
        when(approvalStepRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        approvalService.approveRoom(booking.getId(), request, approver);

        ArgumentCaptor<ApprovalStep> stepCaptor = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(approvalStepRepository).save(stepCaptor.capture());
        assertThat(stepCaptor.getValue().getApprover().getId()).isEqualTo(approver.getId());
        assertThat(stepCaptor.getValue().getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().recipientId()).isEqualTo(requester.getId());
        assertThat(eventCaptor.getValue().senderId()).isEqualTo(approver.getId());
        assertThat(eventCaptor.getValue().type()).isEqualTo(NotificationType.BOOKING_APPROVED);
        assertThat(eventCaptor.getValue().emailInstruction()).isNotNull();
    }

    @Test
    void approveCarUsesAuthenticatedApproverWhenBodyContainsSpoofedApproverId() {
        User requester = user("user-1", "Nhan vien", RoleEnum.EMPLOYEE);
        User approver = user("manager-1", "Quan ly", RoleEnum.MANAGER);
        BookingCar booking = bookingCar("booking-car-1", requester);
        ApprovalRequest request = new ApprovalRequest();
        request.setApproverId("spoofed-admin-id");
        request.setReason("OK");

        when(bookingCarRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findById(approver.getId())).thenReturn(Optional.of(approver));
        when(bookingCarRepository.save(booking)).thenReturn(booking);
        when(approvalStepRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        approvalService.approveCar(booking.getId(), request, approver);

        ArgumentCaptor<ApprovalStep> stepCaptor = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(approvalStepRepository).save(stepCaptor.capture());
        assertThat(stepCaptor.getValue().getApprover().getId()).isEqualTo(approver.getId());
        assertThat(stepCaptor.getValue().getBookingCar().getId()).isEqualTo(booking.getId());
        assertThat(stepCaptor.getValue().getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void approveRoomRejectsEmployeeEvenWhenBodyContainsAdminApproverId() {
        User requester = user("user-1", "Nhan vien", RoleEnum.EMPLOYEE);
        User employee = user("employee-1", "Employee", RoleEnum.EMPLOYEE);
        BookingRoom booking = bookingRoom("booking-room-1", requester);
        ApprovalRequest request = new ApprovalRequest();
        request.setApproverId("admin-1");
        request.setReason("OK");

        when(bookingRoomRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> approvalService.approveRoom(booking.getId(), request, employee))
                .isInstanceOf(AccessDeniedException.class);

        verify(bookingRoomRepository, never()).save(any());
        verify(approvalStepRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectRoomUsesLegacyNoteAsReasonWhenReasonIsMissing() {
        User requester = user("user-1", "Nhan vien", RoleEnum.EMPLOYEE);
        User approver = user("admin-1", "Admin", RoleEnum.ADMIN);
        BookingRoom booking = bookingRoom("booking-room-1", requester);
        ApprovalRequest request = new ApprovalRequest();
        request.setApproverId("spoofed-admin-id");
        request.setNote("Khong phu hop");

        when(bookingRoomRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findById(approver.getId())).thenReturn(Optional.of(approver));
        when(bookingRoomRepository.save(booking)).thenReturn(booking);
        when(approvalStepRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        approvalService.rejectRoom(booking.getId(), request, approver);

        ArgumentCaptor<ApprovalStep> stepCaptor = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(approvalStepRepository).save(stepCaptor.capture());
        assertThat(stepCaptor.getValue().getReason()).isEqualTo("Khong phu hop");
        assertThat(stepCaptor.getValue().getStatus()).isEqualTo(ApprovalStatus.REJECTED);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().emailInstruction()).isNotNull();
        assertThat(eventCaptor.getValue().emailInstruction().reason()).isEqualTo("Khong phu hop");
    }

    @Test
    void rejectRoomAllowsAdminWithoutReason() {
        User requester = user("user-1", "Nhan vien", RoleEnum.EMPLOYEE);
        User admin = user("admin-1", "Admin", RoleEnum.ADMIN);
        BookingRoom booking = bookingRoom("booking-room-1", requester);
        ApprovalRequest request = new ApprovalRequest();

        when(bookingRoomRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        approvalService.rejectRoom(booking.getId(), request, admin);

        ArgumentCaptor<ApprovalStep> stepCaptor = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(approvalStepRepository).save(stepCaptor.capture());
        assertThat(stepCaptor.getValue().getReason()).isNull();

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().message()).doesNotContain("Lý do:").doesNotContain("null");
        assertThat(eventCaptor.getValue().emailInstruction().reason()).isNull();
    }

    @Test
    void rejectRoomRejectsManager() {
        User requester = user("user-1", "Nhan vien", RoleEnum.EMPLOYEE);
        User manager = user("manager-1", "Manager", RoleEnum.MANAGER);
        BookingRoom booking = bookingRoom("booking-room-1", requester);

        assertThatThrownBy(() -> approvalService.rejectRoom(booking.getId(), new ApprovalRequest(), manager))
                .isInstanceOf(AccessDeniedException.class);

        verify(bookingRoomRepository, never()).findById(any());
        verify(bookingRoomRepository, never()).save(any());
        verify(approvalStepRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getRoomApprovalStepsReturnsReasonAndAuthenticatedApprover() {
        User approver = user("manager-1", "Manager", RoleEnum.MANAGER);
        ApprovalStep step = new ApprovalStep();
        step.setId("step-1");
        step.setApprover(approver);
        step.setStatus(ApprovalStatus.REJECTED);
        step.setReason("Khong phu hop");
        step.setActedAt(LocalDateTime.now());

        when(approvalStepRepository.findByBookingRoomIdOrderByActedAtDesc("booking-room-1"))
                .thenReturn(List.of(step));

        var steps = approvalService.getRoomApprovalSteps("booking-room-1");

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getReason()).isEqualTo("Khong phu hop");
        assertThat(steps.get(0).getApprover().getId()).isEqualTo(approver.getId());
        assertThat(steps.get(0).getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    void getHistoryAllowsManagerAndUsesServerSidePagination() {
        User manager = user("manager-1", "Manager", RoleEnum.MANAGER);
        PageRequest pageable = PageRequest.of(1, 20);
        when(approvalStepRepository.findHistory("ROOM", BookingStatus.APPROVED, "nguyen", null, null, false, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = approvalService.getHistory(
                manager, "room", BookingStatus.APPROVED, "  nguyen  ", null, null, false, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(approvalStepRepository).findHistory("ROOM", BookingStatus.APPROVED, "nguyen", null, null, false, pageable);
    }

    @Test
    void getHistoryRejectsEmployeeBeforeQueryingRepository() {
        User employee = user("employee-1", "Employee", RoleEnum.EMPLOYEE);

        assertThatThrownBy(() -> approvalService.getHistory(
                employee, "ALL", null, null, null, null, false, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verify(approvalStepRepository, never()).findHistory(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    private BookingRoom bookingRoom(String id, User requester) {
        BookingRoom booking = new BookingRoom();
        booking.setId(id);
        booking.setTitle("Hop sprint");
        booking.setRequester(requester);
        return booking;
    }

    private BookingCar bookingCar(String id, User requester) {
        BookingCar booking = new BookingCar();
        booking.setId(id);
        booking.setDeparture("Office");
        booking.setDestination("Airport");
        booking.setRequester(requester);
        return booking;
    }

    private User user(String id, String fullName, RoleEnum role) {
        User user = new User();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setFullName(fullName);
        user.setRole(role);
        return user;
    }
}
