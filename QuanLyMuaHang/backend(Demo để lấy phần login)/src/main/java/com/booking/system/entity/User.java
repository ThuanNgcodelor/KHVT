package com.booking.system.entity;

import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@Entity
@Table(name = "users")
@JsonIgnoreProperties({
        "hibernateLazyInitializer", "handler", "password", "department",
        "registrationReviewedBy", "registrationReviewedAt", "registrationReviewReason"
})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "full_name", nullable = false)
    private String fullName;
    
    @Column(name = "password")
    private String password;
    
    @Lob
    @Column(name = "avatar_url", columnDefinition = "MEDIUMTEXT")
    private String avatarUrl;

    @Column(name = "job_position")
    private String jobPosition;
    
    @Enumerated(EnumType.STRING)
    private RoleEnum role;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    private UserStatus status;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_reviewed_by_id")
    private User registrationReviewedBy;

    @Column(name = "registration_reviewed_at")
    private LocalDateTime registrationReviewedAt;

    @Column(name = "registration_review_reason", length = 1000)
    private String registrationReviewReason;
}
