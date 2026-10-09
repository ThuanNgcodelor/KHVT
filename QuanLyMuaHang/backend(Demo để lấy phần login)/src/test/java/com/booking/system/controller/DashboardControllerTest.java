package com.booking.system.controller;

import com.booking.system.dto.ClientDashboardStats;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    private DashboardController controller;
    private User employee;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(dashboardService);
        employee = user("employee-id", RoleEnum.EMPLOYEE);
    }

    @Test
    void clientDashboardAlwaysUsesAuthenticatedPrincipalId() {
        ClientDashboardStats stats = ClientDashboardStats.builder()
                .upcomingBookings(List.of())
                .build();
        when(dashboardService.getClientStats("employee-id")).thenReturn(stats);

        var response = controller.getClientStats(employee);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(stats);
        verify(dashboardService).getClientStats("employee-id");
    }

    @Test
    void legacyClientDashboardRejectsReadingAnotherUsersData() {
        assertThatThrownBy(() -> controller.getClientStatsLegacy("another-user-id", employee))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void legacyClientDashboardStillAllowsOwnerAndAdminForCompatibility() {
        ClientDashboardStats stats = ClientDashboardStats.builder()
                .upcomingBookings(List.of())
                .build();
        User admin = user("admin-id", RoleEnum.ADMIN);
        when(dashboardService.getClientStats("employee-id")).thenReturn(stats);

        controller.getClientStatsLegacy("employee-id", employee);
        controller.getClientStatsLegacy("employee-id", admin);

        verify(dashboardService, org.mockito.Mockito.times(2)).getClientStats("employee-id");
    }

    private static User user(String id, RoleEnum role) {
        User user = new User();
        user.setId(id);
        user.setEmail(id + "@example.test");
        user.setFullName(role.name());
        user.setRole(role);
        return user;
    }
}
