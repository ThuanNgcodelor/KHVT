package com.booking.system.hr.api;

import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.hr.api.dto.HrImportConfirmRequest;
import com.booking.system.hr.enums.HrImportBatchStatus;
import com.booking.system.hr.importer.HrBaselineImportService;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportBatchSummary;
import com.booking.system.hr.importer.HrWorkforceSnapshotImportService;
import com.booking.system.hr.importer.HrWorkforceSnapshotResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrImportControllerTest {

    @Mock
    private HrActivityQueryService queryService;

    @Mock
    private HrBaselineImportService importService;

    @Mock
    private HrWorkforceSnapshotImportService snapshotImportService;

    private HrImportController controller;
    private User manager;

    @BeforeEach
    void setUp() {
        controller = new HrImportController(
                queryService,
                importService,
                snapshotImportService,
                new HrActorResolver()
        );
        manager = new User();
        manager.setId("manager-id");
        manager.setEmail("manager@example.test");
        manager.setFullName("HR Manager");
        manager.setRole(RoleEnum.MANAGER);
        manager.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void validateBuildsActorOnlyFromAuthenticatedPrincipal() {
        HrImportBatchSummary summary = summary("batch-1", HrImportBatchStatus.VALIDATED);
        when(importService.validate(eq("batch-1"), any(HrImportActor.class))).thenReturn(summary);

        controller.validate(manager, "batch-1");

        ArgumentCaptor<HrImportActor> actor = ArgumentCaptor.forClass(HrImportActor.class);
        verify(importService).validate(eq("batch-1"), actor.capture());
        assertThat(actor.getValue()).isEqualTo(new HrImportActor(
                "USER:manager-id",
                "HR Manager",
                "MANAGER"
        ));
    }

    @Test
    void confirmPassesOnlyCommandFieldsAlongsidePrincipalDerivedActor() {
        HrImportBatchSummary summary = summary("batch-1", HrImportBatchStatus.CONFIRMED);
        when(importService.confirm(
                eq("batch-1"),
                eq("confirmation-1"),
                eq(true),
                any(HrImportActor.class)
        )).thenReturn(summary);

        controller.confirm(manager, "batch-1", new HrImportConfirmRequest("confirmation-1", true));

        ArgumentCaptor<HrImportActor> actor = ArgumentCaptor.forClass(HrImportActor.class);
        verify(importService).confirm(eq("batch-1"), eq("confirmation-1"), eq(true), actor.capture());
        assertThat(actor.getValue().subject()).isEqualTo("USER:manager-id");
        assertThat(actor.getValue().role()).isEqualTo("MANAGER");
    }

    @Test
    void workforceSnapshotConfirmUsesAuthenticatedManagerAsActor() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "workforce-baseline-339-2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );
        when(snapshotImportService.confirm(
                eq(file.getOriginalFilename()),
                eq(file.getBytes()),
                eq("snapshot-key"),
                eq(339),
                any(HrImportActor.class)
        )).thenReturn(new HrWorkforceSnapshotResult(
                "batch-339", "hash", 0, 0, 0, 339, 339, true, false
        ));

        controller.confirmWorkforceSnapshot(
                manager,
                file,
                "snapshot-key",
                339
        );

        ArgumentCaptor<HrImportActor> actor = ArgumentCaptor.forClass(HrImportActor.class);
        verify(snapshotImportService).confirm(
                eq(file.getOriginalFilename()),
                eq(file.getBytes()),
                eq("snapshot-key"),
                eq(339),
                actor.capture()
        );
        assertThat(actor.getValue().subject()).isEqualTo("USER:manager-id");
        assertThat(actor.getValue().role()).isEqualTo("MANAGER");
    }

    private static HrImportBatchSummary summary(String id, HrImportBatchStatus status) {
        return new HrImportBatchSummary(
                id, status, 1, 329, 329, 0, 0, status == HrImportBatchStatus.CONFIRMED ? 329 : 0,
                "d8f4ff9e292b68d1ec50b623159ef34095f7441d3487fa1e422140f0fdeaadbe",
                null, null, null, null, null, null
        );
    }
}
