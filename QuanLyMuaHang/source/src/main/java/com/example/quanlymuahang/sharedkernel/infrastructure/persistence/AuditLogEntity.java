package com.example.quanlymuahang.sharedkernel.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(nullable = false, length = 50) private String action;
    @Column(name = "entity_type", nullable = false, length = 80) private String entityType;
    @Column(name = "entity_id") private Long entityId;
    @Column(name = "before_json", columnDefinition = "json") private String beforeJson;
    @Column(name = "after_json", columnDefinition = "json") private String afterJson;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "request_id", length = 64) private String requestId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected AuditLogEntity() {}
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
