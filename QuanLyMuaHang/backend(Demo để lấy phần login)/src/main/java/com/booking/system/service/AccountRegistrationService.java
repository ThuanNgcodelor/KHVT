package com.booking.system.service;

import com.booking.system.dto.AccountRegistrationResponse;
import com.booking.system.entity.User;
import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.NotificationType;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.event.NotificationEvent;
import com.booking.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountRegistrationService {
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<AccountRegistrationResponse> getPending(User principal, Pageable pageable) {
        requireAdmin(principal);
        return userRepository.findByStatusOrderByCreatedAtDesc(UserStatus.PENDING_APPROVAL, pageable)
                .map(AccountRegistrationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countPending(User principal) {
        requireAdmin(principal);
        return userRepository.countByStatus(UserStatus.PENDING_APPROVAL);
    }

    @Transactional
    public AccountRegistrationResponse approve(User principal, String userId) {
        User admin = requireAdmin(principal);
        User account = requirePending(userId);
        account.setRole(RoleEnum.MANAGER);
        account.setStatus(UserStatus.ACTIVE);
        account.setRegistrationReviewedBy(admin);
        account.setRegistrationReviewedAt(LocalDateTime.now());
        account.setRegistrationReviewReason(null);
        account = userRepository.save(account);
        publishResult(account, admin, true, null);
        return AccountRegistrationResponse.from(account);
    }

    @Transactional
    public AccountRegistrationResponse reject(User principal, String userId, String reason) {
        User admin = requireAdmin(principal);
        User account = requirePending(userId);
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();
        account.setStatus(UserStatus.REJECTED);
        account.setRegistrationReviewedBy(admin);
        account.setRegistrationReviewedAt(LocalDateTime.now());
        account.setRegistrationReviewReason(normalizedReason);
        account = userRepository.save(account);
        publishResult(account, admin, false, normalizedReason);
        return AccountRegistrationResponse.from(account);
    }

    private void publishResult(User account, User admin, boolean approved, String reason) {
        NotificationType type = approved
                ? NotificationType.ACCOUNT_REGISTRATION_APPROVED
                : NotificationType.ACCOUNT_REGISTRATION_REJECTED;
        NotificationEvent.EmailType emailType = approved
                ? NotificationEvent.EmailType.ACCOUNT_REGISTRATION_APPROVED
                : NotificationEvent.EmailType.ACCOUNT_REGISTRATION_REJECTED;
        String title = approved ? "Tài khoản đã được phê duyệt" : "Đăng ký tài khoản bị từ chối";
        String message = approved
                ? "Tài khoản quản lý nhân sự của bạn đã được kích hoạt. Bạn có thể đăng nhập vào CFC Base."
                : "Yêu cầu đăng ký tài khoản của bạn không được chấp thuận."
                        + (reason == null ? "" : " Lý do: " + reason);

        eventPublisher.publishEvent(new NotificationEvent(
                account.getId(), admin.getId(), type, title, message, "/login",
                "ACCOUNT_REGISTRATION", account.getId(),
                approved ? NotificationPriority.NORMAL : NotificationPriority.HIGH,
                new NotificationEvent.EmailInstruction(
                        emailType, "tài khoản", account.getFullName(), title, reason)));
    }

    private User requirePending(String userId) {
        User account = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));
        if (account.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Tài khoản này đã được xử lý");
        }
        return account;
    }

    private User requireAdmin(User principal) {
        if (principal == null || principal.getId() == null || principal.getRole() != RoleEnum.ADMIN) {
            throw new AccessDeniedException("Chỉ quản trị viên được duyệt tài khoản");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy quản trị viên"));
    }
}
