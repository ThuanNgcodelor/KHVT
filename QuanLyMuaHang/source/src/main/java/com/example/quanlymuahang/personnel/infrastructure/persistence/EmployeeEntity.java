package com.example.quanlymuahang.personnel.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "employees")
public class EmployeeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 80)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(length = 320, unique = true)
    private String email;

    @Column(length = 50)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private DepartmentEntity department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private PositionEntity position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private EmployeeEntity manager;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    @Column(name = "left_at")
    private LocalDate leftAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeEntity() {
    }

    public EmployeeEntity(String employeeCode, String fullName, String email, String phone,
                          DepartmentEntity department, PositionEntity position, LocalDate joinedAt) {
        this.employeeCode = employeeCode.trim();
        this.fullName = fullName.trim();
        this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        this.phone = phone;
        this.department = department;
        this.position = position;
        this.joinedAt = joinedAt;
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public void update(String fullName, String email, String phone, DepartmentEntity department,
                       PositionEntity position, LocalDate joinedAt) {
        this.fullName = fullName.trim();
        this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        this.phone = phone;
        this.department = department;
        this.position = position;
        this.joinedAt = joinedAt;
    }

    public void deactivate(LocalDate leftAt) { this.status = EmployeeStatus.INACTIVE; this.leftAt = leftAt; }
    public void activate() { this.status = EmployeeStatus.ACTIVE; this.leftAt = null; }
    public Long getId() { return id; }
    public String getEmployeeCode() { return employeeCode; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public DepartmentEntity getDepartment() { return department; }
    public PositionEntity getPosition() { return position; }
    public EmployeeStatus getStatus() { return status; }
    public LocalDate getJoinedAt() { return joinedAt; }
    public LocalDate getLeftAt() { return leftAt; }
    public long getVersion() { return version; }
}
