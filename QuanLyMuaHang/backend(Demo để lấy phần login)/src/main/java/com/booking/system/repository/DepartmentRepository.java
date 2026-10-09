package com.booking.system.repository;

import com.booking.system.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, String> {
    List<Department> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
