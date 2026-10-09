package com.example.quanlymuahang.personnel.domain.port;

import com.example.quanlymuahang.personnel.domain.model.Employee;

import java.util.Optional;

public interface EmployeeRepository {
    Optional<Employee> findById(Long id);
    Employee save(Employee employee);
}
