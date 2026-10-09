package com.booking.system.hr.api;

import com.booking.system.hr.api.dto.HrLeaveSyncItemResponse;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.service.HrLeaveSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HrLeaveSyncControllerTest {

    @Mock
    private HrLeaveSyncService syncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HrLeaveSyncController(syncService))
                .setControllerAdvice(new HrApiExceptionHandler())
                .build();
    }

    @Test
    void getLeaveSyncRosterReturnsJsonList() throws Exception {
        HrLeaveSyncItemResponse item = new HrLeaveSyncItemResponse(
                "A268",
                "Nguyễn Công Huân",
                "Tổng Giám đốc",
                "Tổng Giám đốc",
                LocalDate.of(2013, 4, 15),
                "Bình Thường",
                "13 NĂM 4 THÁNG 17 NGÀY ",
                BigDecimal.valueOf(14.0),
                HrEmploymentStatus.ACTIVE,
                null,
                "T8-26"
        );

        when(syncService.getLeaveSyncRoster("T8-26", false)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/hr/sync/leave-roster")
                        .param("period", "T8-26")
                        .param("activeOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].employeeCode").value("A268"))
                .andExpect(jsonPath("$.data[0].fullName").value("Nguyễn Công Huân"))
                .andExpect(jsonPath("$.data[0].annualLeaveDays").value(14.0))
                .andExpect(jsonPath("$.data[0].employmentStatus").value("ACTIVE"));
    }
}
