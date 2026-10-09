package com.booking.system.hr.service;

import com.booking.system.hr.api.dto.HrLeaveSyncItemResponse;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import com.booking.system.hr.entity.HrEmployeeMovement;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrMovementStatus;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HrLeaveSyncServiceTest {

    private final HrRosterProjectionService rosterProjectionService = mock(HrRosterProjectionService.class);
    private final HrEmployeeMovementRepository movementRepository = mock(HrEmployeeMovementRepository.class);
    private final HrLeaveSyncService syncService = new HrLeaveSyncService(rosterProjectionService, movementRepository);

    @Test
    void getLeaveSyncRosterReturnsActiveAndInactiveEmployees() {
        HrEmployee activeEmployee = new HrEmployee();
        activeEmployee.setId("emp-1");
        activeEmployee.setEmployeeCode("A268");
        activeEmployee.setFullName("Nguyễn Công Huân");

        HrRosterProjectionService.ProjectedRosterItem activeItem = new HrRosterProjectionService.ProjectedRosterItem(
                "proj-1",
                activeEmployee,
                1,
                1,
                "A268",
                "Nguyễn Công Huân",
                "BGD",
                "Ban Giám Đốc",
                "TGĐ",
                "Tổng Giám Đốc",
                "BT",
                "Bình Thường",
                HrEmploymentStatus.ACTIVE,
                LocalDate.of(2013, 4, 15),
                null,
                BigDecimal.valueOf(14.0),
                null,
                null,
                null,
                null
        );

        when(rosterProjectionService.projectedItems(any(LocalDate.class))).thenReturn(List.of(activeItem));

        HrEmployee inactiveEmployee = new HrEmployee();
        inactiveEmployee.setId("emp-2");
        inactiveEmployee.setEmployeeCode("A381");
        inactiveEmployee.setFullName("Nguyễn Thị Phương Lan");

        HrEmployeeEmployment employment = new HrEmployeeEmployment();
        employment.setHireDate(LocalDate.of(2020, 1, 1));
        inactiveEmployee.setEmployment(employment);

        HrEmployeeMovement decreaseMovement = new HrEmployeeMovement();
        decreaseMovement.setId("mov-1");
        decreaseMovement.setEmployee(inactiveEmployee);
        decreaseMovement.setMovementType(HrMovementType.DECREASE);
        decreaseMovement.setEffectiveDate(LocalDate.of(2026, 8, 1));

        when(movementRepository.findConfirmedForProjection(
                eq(HrMovementStatus.CONFIRMED),
                eq(EnumSet.of(HrMovementType.DECREASE))
        )).thenReturn(List.of(decreaseMovement));

        List<HrLeaveSyncItemResponse> result = syncService.getLeaveSyncRoster("T8-26", false);

        assertThat(result).hasSize(2);
        
        HrLeaveSyncItemResponse activeResult = result.get(0);
        assertThat(activeResult.employeeCode()).isEqualTo("A268");
        assertThat(activeResult.employmentStatus()).isEqualTo(HrEmploymentStatus.ACTIVE);
        assertThat(activeResult.annualLeaveDays()).isEqualTo(BigDecimal.valueOf(14.0));
        assertThat(activeResult.serviceYears()).contains("NĂM").contains("THÁNG").contains("NGÀY");

        HrLeaveSyncItemResponse inactiveResult = result.get(1);
        assertThat(inactiveResult.employeeCode()).isEqualTo("A381");
        assertThat(inactiveResult.employmentStatus()).isEqualTo(HrEmploymentStatus.INACTIVE);
        assertThat(inactiveResult.annualLeaveDays()).isEqualTo(BigDecimal.ZERO);
        assertThat(inactiveResult.resignationDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }
}
