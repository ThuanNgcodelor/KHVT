package com.booking.system.entity;

import com.booking.system.enums.ProfileUpdateRequestStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "profile_update_requests",
        indexes = {
                @Index(name = "idx_profile_update_requests_status_requested", columnList = "status,requested_at"),
                @Index(name = "idx_profile_update_requests_requester", columnList = "requester_id,status")
        }
)
public class ProfileUpdateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "department"})
    private User requester;

    @Column(name = "current_full_name", nullable = false)
    private String currentFullName;

    @Column(name = "current_avatar_url", length = 1000)
    private String currentAvatarUrl;

    @Column(name = "current_department_name")
    private String currentDepartmentName;

    @Column(name = "current_position")
    private String currentPosition;

    @Column(name = "requested_full_name", nullable = false)
    private String requestedFullName;

    @Column(name = "requested_avatar_url", length = 1000)
    private String requestedAvatarUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_department_id")
    private Department requestedDepartment;

    @Column(name = "requested_position")
    private String requestedPosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileUpdateRequestStatus status = ProfileUpdateRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "department"})
    private User reviewedBy;

    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
