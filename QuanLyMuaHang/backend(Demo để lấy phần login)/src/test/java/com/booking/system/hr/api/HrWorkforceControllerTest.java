package com.booking.system.hr.api;

import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.hr.api.dto.HrMovementCreateRequest;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.service.HrLeaveEntitlementService;
import com.booking.system.hr.service.HrWorkforceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HrWorkforceControllerTest {

    @Mock
    private HrWorkforceService workforceService;
    @Mock
    private HrLeaveEntitlementService leaveEntitlementService;

    private HrWorkforceController controller;
    private User manager;

    @BeforeEach
    void setUp() {
        controller = new HrWorkforceController(workforceService, leaveEntitlementService, new HrActorResolver());
        manager = new User();
        manager.setId("manager-id");
        manager.setEmail("manager@example.test");
        manager.setFullName("HR Manager");
        manager.setRole(RoleEnum.MANAGER);
        manager.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void createMovementUsesOnlyAuthenticatedManagerAsActor() {
        HrMovementCreateRequest request = new HrMovementCreateRequest(
                "employee-id", HrMovementType.INCREASE, LocalDate.of(2026, 7, 20),
                "Bổ sung nhân sự", null, null, "movement-command-1"
        );

        controller.createMovement(manager, request);

        ArgumentCaptor<HrImportActor> actor = ArgumentCaptor.forClass(HrImportActor.class);
        verify(workforceService).createMovement(eq(request), actor.capture());
        assertThat(actor.getValue()).isEqualTo(new HrImportActor(
                "USER:manager-id", "HR Manager", "MANAGER"
        ));
    }

    @Test
    void deleteDraftEmployeeUsesPrincipalActorAndVersionGuard() {
        controller.deleteDraftEmployee(manager, "employee-id", 4L);

        ArgumentCaptor<HrImportActor> actor = ArgumentCaptor.forClass(HrImportActor.class);
        verify(workforceService).deleteDraftEmployee(eq("employee-id"), eq(4L), actor.capture());
        assertThat(actor.getValue().subject()).isEqualTo("USER:manager-id");
        assertThat(actor.getValue().role()).isEqualTo("MANAGER");
    }
}
