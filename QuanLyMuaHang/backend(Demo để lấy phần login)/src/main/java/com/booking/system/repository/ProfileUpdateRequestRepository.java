package com.booking.system.repository;

import com.booking.system.entity.ProfileUpdateRequest;
import com.booking.system.enums.ProfileUpdateRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileUpdateRequestRepository extends JpaRepository<ProfileUpdateRequest, String> {
    boolean existsByRequesterIdAndStatus(String requesterId, ProfileUpdateRequestStatus status);

    @EntityGraph(attributePaths = {"requester.department", "requestedDepartment", "reviewedBy.department"})
    Optional<ProfileUpdateRequest> findTopByRequesterIdOrderByRequestedAtDesc(String requesterId);

    @EntityGraph(attributePaths = {"requester.department", "requestedDepartment", "reviewedBy.department"})
    List<ProfileUpdateRequest> findByRequesterIdOrderByRequestedAtDesc(String requesterId);

    @EntityGraph(attributePaths = {"requester.department", "requestedDepartment", "reviewedBy.department"})
    Page<ProfileUpdateRequest> findByStatusOrderByRequestedAtDesc(ProfileUpdateRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"requester.department", "requestedDepartment", "reviewedBy.department"})
    Optional<ProfileUpdateRequest> findById(String id);

    @EntityGraph(attributePaths = {"requester.department", "requestedDepartment", "reviewedBy.department"})
    Optional<ProfileUpdateRequest> findByIdAndRequesterId(String id, String requesterId);
}
