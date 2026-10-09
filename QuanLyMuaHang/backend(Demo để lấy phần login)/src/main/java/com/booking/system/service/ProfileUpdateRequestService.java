package com.booking.system.service;

import com.booking.system.dto.ProfileUpdateRequestCreateRequest;
import com.booking.system.dto.ProfileUpdateRequestResponse;
import com.booking.system.entity.Department;
import com.booking.system.entity.ProfileUpdateRequest;
import com.booking.system.entity.User;
import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.NotificationType;
import com.booking.system.enums.ProfileUpdateRequestStatus;
import com.booking.system.enums.RoleEnum;
import com.booking.system.event.NotificationEvent;
import com.booking.system.repository.DepartmentRepository;
import com.booking.system.repository.ProfileUpdateRequestRepository;
import com.booking.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileUpdateRequestService {

    private final ProfileUpdateRequestRepository profileUpdateRequestRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProfileUpdateRequestResponse submitRequest(String requesterId, ProfileUpdateRequestCreateRequest request) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (profileUpdateRequestRepository.existsByRequesterIdAndStatus(requesterId, ProfileUpdateRequestStatus.PENDING)) {
            throw new RuntimeException("Bạn đang có một yêu cầu cập nhật hồ sơ chờ duyệt");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Phòng ban không tồn tại"));

        String requestedFullName = request.getFullName().trim();
        String requestedPosition = request.getPosition().trim();

        if (requestedFullName.equalsIgnoreCase(requester.getFullName())
                && safeEquals(department.getId(), requester.getDepartment() == null ? null : requester.getDepartment().getId())
                && safeEquals(requestedPosition, requester.getJobPosition())) {
            throw new RuntimeException("Thông tin mới trùng với thông tin hiện tại");
        }

        ProfileUpdateRequest profileUpdateRequest = new ProfileUpdateRequest();
        profileUpdateRequest.setRequester(requester);
        profileUpdateRequest.setCurrentFullName(requester.getFullName());
        profileUpdateRequest.setCurrentAvatarUrl(null);
        profileUpdateRequest.setCurrentDepartmentName(requester.getDepartment() == null ? null : requester.getDepartment().getName());
        profileUpdateRequest.setCurrentPosition(requester.getJobPosition());
        profileUpdateRequest.setRequestedFullName(requestedFullName);
        profileUpdateRequest.setRequestedAvatarUrl(null);
        profileUpdateRequest.setRequestedDepartment(department);
        profileUpdateRequest.setRequestedPosition(requestedPosition);
        profileUpdateRequest.setStatus(ProfileUpdateRequestStatus.PENDING);
        profileUpdateRequest = profileUpdateRequestRepository.save(profileUpdateRequest);

        publishRequestNotifications(profileUpdateRequest);
        return ProfileUpdateRequestResponse.from(profileUpdateRequest);
    }

    @Transactional(readOnly = true)
    public Page<ProfileUpdateRequestResponse> getPendingRequests(Pageable pageable) {
        return profileUpdateRequestRepository.findByStatusOrderByRequestedAtDesc(ProfileUpdateRequestStatus.PENDING, pageable)
                .map(ProfileUpdateRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ProfileUpdateRequestResponse> getMyRequests(String requesterId) {
        return profileUpdateRequestRepository.findByRequesterIdOrderByRequestedAtDesc(requesterId)
                .stream()
                .map(ProfileUpdateRequestResponse::from)
                .toList();
    }

    @Transactional
    public ProfileUpdateRequestResponse approve(String requestId, String approverId) {
        ProfileUpdateRequest profileUpdateRequest = profileUpdateRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cập nhật hồ sơ"));

        if (profileUpdateRequest.getStatus() != ProfileUpdateRequestStatus.PENDING) {
            throw new RuntimeException("Yêu cầu này đã được xử lý");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Người duyệt không tồn tại"));
        if (approver.getRole() != RoleEnum.ADMIN) {
            throw new RuntimeException("Chỉ quản trị viên mới có quyền phê duyệt hồ sơ");
        }

        User requester = profileUpdateRequest.getRequester();
        requester.setFullName(profileUpdateRequest.getRequestedFullName());
        requester.setDepartment(profileUpdateRequest.getRequestedDepartment());
        requester.setJobPosition(profileUpdateRequest.getRequestedPosition());
        userRepository.save(requester);

        profileUpdateRequest.setStatus(ProfileUpdateRequestStatus.APPROVED);
        profileUpdateRequest.setReviewedBy(approver);
        profileUpdateRequest.setReviewedAt(LocalDateTime.now());
        profileUpdateRequest = profileUpdateRequestRepository.save(profileUpdateRequest);

        eventPublisher.publishEvent(new NotificationEvent(
                requester.getId(),
                approver.getId(),
                NotificationType.PROFILE_UPDATE_APPROVED,
                "Hồ sơ của bạn đã được phê duyệt",
                "Thông tin hồ sơ của bạn đã được cập nhật.",
                "/profile",
                "PROFILE_UPDATE_REQUEST",
                profileUpdateRequest.getId(),
                NotificationPriority.NORMAL,
                new NotificationEvent.EmailInstruction(
                        NotificationEvent.EmailType.PROFILE_UPDATE_APPROVED,
                        "hồ sơ",
                        requester.getFullName(),
                        summarizeRequest(profileUpdateRequest),
                        null
                )
        ));

        return ProfileUpdateRequestResponse.from(profileUpdateRequest);
    }

    @Transactional
    public ProfileUpdateRequestResponse reject(String requestId, String approverId, String reason) {
        ProfileUpdateRequest profileUpdateRequest = profileUpdateRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cập nhật hồ sơ"));

        if (profileUpdateRequest.getStatus() != ProfileUpdateRequestStatus.PENDING) {
            throw new RuntimeException("Yêu cầu này đã được xử lý");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Người duyệt không tồn tại"));
        if (approver.getRole() != RoleEnum.ADMIN) {
            throw new RuntimeException("Chỉ quản trị viên mới có quyền phê duyệt hồ sơ");
        }

        profileUpdateRequest.setStatus(ProfileUpdateRequestStatus.REJECTED);
        profileUpdateRequest.setReviewedBy(approver);
        profileUpdateRequest.setReviewedAt(LocalDateTime.now());
        profileUpdateRequest.setReviewReason(reason);
        profileUpdateRequest = profileUpdateRequestRepository.save(profileUpdateRequest);

        eventPublisher.publishEvent(new NotificationEvent(
                profileUpdateRequest.getRequester().getId(),
                approver.getId(),
                NotificationType.PROFILE_UPDATE_REJECTED,
                "Yêu cầu cập nhật hồ sơ bị từ chối",
                buildRejectMessage(profileUpdateRequest, reason),
                "/profile",
                "PROFILE_UPDATE_REQUEST",
                profileUpdateRequest.getId(),
                NotificationPriority.HIGH,
                new NotificationEvent.EmailInstruction(
                        NotificationEvent.EmailType.PROFILE_UPDATE_REJECTED,
                        "hồ sơ",
                        profileUpdateRequest.getRequester().getFullName(),
                        summarizeRequest(profileUpdateRequest),
                        reason
                )
        ));

        return ProfileUpdateRequestResponse.from(profileUpdateRequest);
    }

    private void publishRequestNotifications(ProfileUpdateRequest request) {
        List<User> admins = userRepository.findByRole(RoleEnum.ADMIN);
        String title = "Yêu cầu cập nhật hồ sơ mới";
        String message = request.getRequester().getFullName() + " vừa gửi yêu cầu cập nhật hồ sơ.";
        String summary = summarizeRequest(request);

        for (User admin : admins) {
            eventPublisher.publishEvent(new NotificationEvent(
                    admin.getId(),
                    request.getRequester().getId(),
                    NotificationType.PROFILE_UPDATE_REQUESTED,
                    title,
                    message,
                    "/admin/profile-approvals/" + request.getId(),
                    "PROFILE_UPDATE_REQUEST",
                    request.getId(),
                    NotificationPriority.HIGH,
                    new NotificationEvent.EmailInstruction(
                            NotificationEvent.EmailType.PROFILE_UPDATE_REQUESTED_TO_ADMIN,
                            "hồ sơ",
                            request.getRequester().getFullName(),
                            summary,
                            null
                    )
            ));
        }
    }

    private String summarizeRequest(ProfileUpdateRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("Họ tên: ").append(request.getRequestedFullName());
        if (request.getRequestedDepartment() != null) {
            builder.append(" | Phòng ban: ").append(request.getRequestedDepartment().getName());
        }
        if (StringUtils.hasText(request.getRequestedPosition())) {
            builder.append(" | Chức vụ: ").append(request.getRequestedPosition());
        }
        return builder.toString();
    }

    private String buildRejectMessage(ProfileUpdateRequest request, String reason) {
        return "Yêu cầu cập nhật hồ sơ của bạn đã bị từ chối."
                + (StringUtils.hasText(reason) ? " Lý do: " + reason : "");
    }

    private boolean safeEquals(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }
}
