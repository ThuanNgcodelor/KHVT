package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.DepartmentResponse;
import com.booking.system.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(
                departmentRepository.findAllByOrderByNameAsc().stream().map(DepartmentResponse::from).toList(),
                "Lấy danh sách phòng ban thành công"
        ));
    }
}
