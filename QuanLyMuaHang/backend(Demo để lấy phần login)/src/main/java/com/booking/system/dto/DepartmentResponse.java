package com.booking.system.dto;

import com.booking.system.entity.Department;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DepartmentResponse {
    private String id;
    private String name;

    public static DepartmentResponse from(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .build();
    }
}
