package com.example.quanlymuahang.personnel.domain.model;

import java.util.Objects;

public final class Employee {
    private final Long id;
    private final String employeeCode;
    private String fullName;
    private Long departmentId;
    private Long positionId;
    private boolean active = true;

    public Employee(Long id, String employeeCode, String fullName) {
        this.id = id;
        this.employeeCode = Objects.requireNonNull(employeeCode, "employeeCode");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
    }

    public void update(String fullName, Long departmentId, Long positionId) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.departmentId = departmentId;
        this.positionId = positionId;
    }

    public void deactivate() { active = false; }
    public Long id() { return id; }
    public String employeeCode() { return employeeCode; }
    public String fullName() { return fullName; }
    public Long departmentId() { return departmentId; }
    public Long positionId() { return positionId; }
    public boolean active() { return active; }
}
