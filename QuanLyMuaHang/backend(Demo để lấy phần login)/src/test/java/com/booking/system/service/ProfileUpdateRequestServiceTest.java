package com.booking.system.service;

import com.booking.system.dto.ProfileUpdateRequestCreateRequest;
import com.booking.system.entity.Department;
import com.booking.system.entity.ProfileUpdateRequest;
import com.booking.system.entity.User;
import com.booking.system.enums.ProfileUpdateRequestStatus;
import com.booking.system.repository.DepartmentRepository;
import com.booking.system.repository.ProfileUpdateRequestRepository;
import com.booking.system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileUpdateRequestServiceTest {

    @Mock
    private ProfileUpdateRequestRepository profileUpdateRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProfileUpdateRequestService profileUpdateRequestService;

    @Test
    void profileApprovalRequestDoesNotPersistAvatarData() {
        Department currentDepartment = department("department-1", "Phòng hiện tại");
        Department requestedDepartment = department("department-2", "Phòng mới");
        User requester = new User();
        requester.setId("user-1");
        requester.setFullName("Tên hiện tại");
        requester.setAvatarUrl("data:image/jpeg;base64,very-large-avatar");
        requester.setDepartment(currentDepartment);
        requester.setJobPosition("Nhân viên");

        ProfileUpdateRequestCreateRequest request = new ProfileUpdateRequestCreateRequest();
        request.setFullName("Tên mới");
        request.setAvatarUrl("data:image/jpeg;base64,legacy-client-avatar");
        request.setDepartmentId(requestedDepartment.getId());
        request.setPosition("Chuyên viên");

        when(userRepository.findById(requester.getId())).thenReturn(Optional.of(requester));
        when(profileUpdateRequestRepository.existsByRequesterIdAndStatus(requester.getId(), ProfileUpdateRequestStatus.PENDING))
                .thenReturn(false);
        when(departmentRepository.findById(requestedDepartment.getId())).thenReturn(Optional.of(requestedDepartment));
        when(profileUpdateRequestRepository.save(any(ProfileUpdateRequest.class))).thenAnswer(invocation -> {
            ProfileUpdateRequest saved = invocation.getArgument(0);
            saved.setId("request-1");
            return saved;
        });
        when(userRepository.findByRole(any())).thenReturn(List.of());

        profileUpdateRequestService.submitRequest(requester.getId(), request);

        ArgumentCaptor<ProfileUpdateRequest> captor = ArgumentCaptor.forClass(ProfileUpdateRequest.class);
        verify(profileUpdateRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentAvatarUrl()).isNull();
        assertThat(captor.getValue().getRequestedAvatarUrl()).isNull();
    }

    private Department department(String id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        return department;
    }
}
