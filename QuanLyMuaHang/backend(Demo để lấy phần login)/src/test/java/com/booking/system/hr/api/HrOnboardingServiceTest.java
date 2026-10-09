package com.booking.system.hr.api;

import com.booking.system.hr.api.dto.HrOnboardingDtos;
import com.booking.system.hr.dto.HrApiDtos;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmploymentContract;
import com.booking.system.hr.enums.HrEmployeeGender;
import com.booking.system.hr.enums.HrEmploymentContractStatus;
import com.booking.system.hr.enums.HrEmploymentContractType;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrOnboardingSource;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.service.HrEmploymentContractService;
import com.booking.system.hr.service.HrManagementService;
import com.booking.system.hr.service.HrOnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrOnboardingServiceTest {

    @Mock private HrManagementService managementService;
    @Mock private HrEmploymentContractService contractService;
    @Mock private HrEmployeeRepository employeeRepository;

    private HrOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new HrOnboardingService(managementService, contractService, employeeRepository);
    }

    @Test
    void directGeneralLaborCreatesPolicyTwoDraftAndNormalizesHireDate() {
        HrOnboardingDtos.GeneralLaborOnboardingRequest request = request(null);
        HrApiDtos.EmployeeDetail detail = detail("employee-1");
        HrEmployee employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmployeeCode("LD001");
        employee.setWorkforceGroup(HrWorkforceGroup.GENERAL_LABOR);
        HrEmploymentContract contract = new HrEmploymentContract();
        contract.setId("contract-1");
        contract.setEmployee(employee);
        contract.setStatus(HrEmploymentContractStatus.READY);

        when(contractService.findByIdempotencyKey("general-labor-1")).thenReturn(Optional.empty());
        when(managementService.createEmployeeForOnboarding(
                any(), eq(HrWorkforceGroup.GENERAL_LABOR), eq(HrOnboardingSource.DIRECT_GENERAL_LABOR), any()))
                .thenReturn(detail);
        when(employeeRepository.findDetailById("employee-1")).thenReturn(Optional.of(employee));
        when(contractService.createReadyContract(eq(employee), eq(null), any(), eq("general-labor-1"), any()))
                .thenReturn(contract);
        when(managementService.getEmployee("employee-1")).thenReturn(detail);
        when(contractService.toSummary(contract)).thenReturn(contractSummary());

        HrOnboardingDtos.GeneralLaborOnboardingResponse response = service.onboardGeneralLabor(request, actor());

        ArgumentCaptor<HrApiDtos.CreateEmployeeRequest> employeeRequest =
                ArgumentCaptor.forClass(HrApiDtos.CreateEmployeeRequest.class);
        verify(managementService).createEmployeeForOnboarding(
                employeeRequest.capture(),
                eq(HrWorkforceGroup.GENERAL_LABOR),
                eq(HrOnboardingSource.DIRECT_GENERAL_LABOR),
                any()
        );
        assertThat(employeeRequest.getValue().employment().hireDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(response.employee().id()).isEqualTo("employee-1");
        assertThat(response.nextAction()).isEqualTo("CREATE_INCREASE");
    }

    @Test
    void directGeneralLaborRejectsHireDateDifferentFromContractStart() {
        HrOnboardingDtos.GeneralLaborOnboardingRequest request = request(LocalDate.of(2026, 8, 14));

        assertThatThrownBy(() -> service.onboardGeneralLabor(request, actor()))
                .isInstanceOf(HrApiException.class)
                .satisfies(error -> assertThat(((HrApiException) error).code())
                        .isEqualTo("HIRE_DATE_CONTRACT_DATE_MISMATCH"));
        verify(managementService, never()).createEmployeeForOnboarding(any(), any(), any(), any());
    }

    @Test
    void directGeneralLaborRejectsIdempotencyReplayWithDifferentContractPayload() {
        HrOnboardingDtos.GeneralLaborOnboardingRequest request = request(null);
        HrEmployee employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmployeeCode("LD001");
        employee.setWorkforceGroup(HrWorkforceGroup.GENERAL_LABOR);
        HrEmploymentContract replay = new HrEmploymentContract();
        replay.setId("contract-1");
        replay.setEmployee(employee);

        when(contractService.findByIdempotencyKey("general-labor-1")).thenReturn(Optional.of(replay));
        when(contractService.matchesInput(replay, request.contract())).thenReturn(false);

        assertThatThrownBy(() -> service.onboardGeneralLabor(request, actor()))
                .isInstanceOf(HrApiException.class)
                .satisfies(error -> assertThat(((HrApiException) error).code())
                        .isEqualTo("ONBOARDING_IDEMPOTENCY_CONFLICT"));
        verify(managementService, never()).createEmployeeForOnboarding(any(), any(), any(), any());
    }

    private static HrOnboardingDtos.GeneralLaborOnboardingRequest request(LocalDate hireDate) {
        HrApiDtos.CreateEmployeeRequest employee = new HrApiDtos.CreateEmployeeRequest(
                new HrApiDtos.PersonalInput(
                        "LD001", "Nguyễn Văn Lao Động", HrEmployeeGender.MALE,
                        null, null, null, null, null, null, null),
                new HrApiDtos.EmploymentInput(
                        "department-1", "position-1", null, hireDate,
                        null, null, null, null, null, null, null),
                null, null, null
        );
        return new HrOnboardingDtos.GeneralLaborOnboardingRequest(
                "general-labor-1",
                employee,
                new HrApiDtos.EmploymentContractInput(
                        HrEmploymentContractType.FIXED_TERM_12_MONTHS,
                        "001/HDLD/2026",
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2027, 8, 15)
                )
        );
    }

    private static HrApiDtos.EmployeeDetail detail(String id) {
        return new HrApiDtos.EmployeeDetail(
                id, HrEmploymentStatus.DRAFT, null, 0L, null, null,
                HrWorkforceGroup.GENERAL_LABOR,
                HrOnboardingSource.DIRECT_GENERAL_LABOR,
                (short) 2,
                null, null, null, null, null, null
        );
    }

    private static HrApiDtos.EmploymentContractSummary contractSummary() {
        return new HrApiDtos.EmploymentContractSummary(
                "contract-1", HrEmploymentContractType.FIXED_TERM_12_MONTHS,
                "001/HDLD/2026", LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 15), LocalDate.of(2027, 8, 15),
                HrEmploymentContractStatus.READY, 0L
        );
    }

    private static HrImportActor actor() {
        return new HrImportActor("USER:manager-1", "HR Manager", "MANAGER");
    }
}
