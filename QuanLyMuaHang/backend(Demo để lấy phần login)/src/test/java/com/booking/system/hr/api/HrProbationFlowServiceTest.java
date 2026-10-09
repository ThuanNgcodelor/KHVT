package com.booking.system.hr.api;

import com.booking.system.hr.api.dto.HrProbationDtos;
import com.booking.system.hr.dto.HrApiDtos;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmploymentContract;
import com.booking.system.hr.entity.HrProbationCandidate;
import com.booking.system.hr.enums.HrEmployeeGender;
import com.booking.system.hr.enums.HrEmploymentContractStatus;
import com.booking.system.hr.enums.HrEmploymentContractType;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrOnboardingSource;
import com.booking.system.hr.enums.HrProbationCandidateStatus;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrDepartmentRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrPositionRepository;
import com.booking.system.hr.repository.HrProbationCandidateRepository;
import com.booking.system.hr.repository.HrProbationContractRepository;
import com.booking.system.hr.repository.HrProbationJobTemplateRepository;
import com.booking.system.hr.repository.HrWorkingConditionRepository;
import com.booking.system.hr.service.HrEmploymentContractService;
import com.booking.system.hr.service.HrManagementService;
import com.booking.system.hr.service.HrProbationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

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
class HrProbationFlowServiceTest {

    @Mock private HrProbationCandidateRepository candidateRepository;
    @Mock private HrProbationContractRepository probationContractRepository;
    @Mock private HrProbationJobTemplateRepository jobTemplateRepository;
    @Mock private HrEmployeeRepository employeeRepository;
    @Mock private HrDepartmentRepository departmentRepository;
    @Mock private HrPositionRepository positionRepository;
    @Mock private HrWorkingConditionRepository workingConditionRepository;
    @Mock private HrManagementService managementService;
    @Mock private HrEmploymentContractService employmentContractService;
    @Mock private HrAuditEventRepository auditRepository;

    private HrProbationService service;

    @BeforeEach
    void setUp() {
        service = new HrProbationService(
                candidateRepository,
                probationContractRepository,
                jobTemplateRepository,
                employeeRepository,
                departmentRepository,
                positionRepository,
                workingConditionRepository,
                managementService,
                employmentContractService,
                auditRepository,
                new HrImportJsonCodec()
        );
    }

    @Test
    void legacyConversionEndpointCannotBypassFormalContract() {
        HrProbationCandidate candidate = candidate(HrProbationCandidateStatus.PASSED);
        when(candidateRepository.findDetailByIdForUpdate("candidate-1")).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.convertToEmployeeDraft(
                "candidate-1",
                new HrProbationDtos.ConvertToEmployeeDraftRequest(0L, "NV001", null),
                actor()))
                .isInstanceOf(HrApiException.class)
                .satisfies(error -> assertThat(((HrApiException) error).code())
                        .isEqualTo("EMPLOYMENT_CONTRACT_REQUIRED"));
    }

    @Test
    void probationCannotStartBeforeTrialContractExists() {
        HrProbationCandidate candidate = candidate(HrProbationCandidateStatus.DRAFT);
        when(candidateRepository.findDetailByIdForUpdate("candidate-1")).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.startProbation(
                "candidate-1",
                new HrProbationDtos.CandidateActionRequest(0L, null),
                actor()))
                .isInstanceOf(HrApiException.class)
                .satisfies(error -> assertThat(((HrApiException) error).code())
                        .isEqualTo("PROBATION_CONTRACT_REQUIRED"));
    }

    @Test
    void passedCandidateBecomesOfficeDraftWithFormalContract() {
        HrProbationCandidate candidate = candidate(HrProbationCandidateStatus.PASSED);
        HrApiDtos.EmployeeDetail detail = new HrApiDtos.EmployeeDetail(
                "employee-1", HrEmploymentStatus.DRAFT, null, 0L, null, null,
                HrWorkforceGroup.OFFICE, HrOnboardingSource.PROBATION, (short) 2,
                null, null, null, null, null, null
        );
        HrEmployee employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmployeeCode("NV001");
        HrEmploymentContract contract = new HrEmploymentContract();
        contract.setId("contract-1");
        contract.setEmployee(employee);
        contract.setSourceProbationCandidate(candidate);
        contract.setStatus(HrEmploymentContractStatus.READY);

        when(employmentContractService.findByIdempotencyKey("office-onboarding-1"))
                .thenReturn(Optional.empty());
        when(candidateRepository.findDetailByIdForUpdate("candidate-1")).thenReturn(Optional.of(candidate));
        when(managementService.createEmployeeForOnboarding(
                any(), eq(HrWorkforceGroup.OFFICE), eq(HrOnboardingSource.PROBATION), any()))
                .thenReturn(detail);
        when(employeeRepository.findDetailById("employee-1")).thenReturn(Optional.of(employee));
        when(employmentContractService.createReadyContract(
                eq(employee), eq(candidate), any(), eq("office-onboarding-1"), any()))
                .thenReturn(contract);
        when(candidateRepository.findDetailById("candidate-1")).thenReturn(Optional.of(candidate));
        when(probationContractRepository.findLatestByCandidateId(eq("candidate-1"), any()))
                .thenReturn(Page.empty());
        when(managementService.getEmployee("employee-1")).thenReturn(detail);
        when(employmentContractService.toSummary(contract)).thenReturn(new HrApiDtos.EmploymentContractSummary(
                "contract-1", HrEmploymentContractType.INDEFINITE, "002/HDLD/2026",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 15), null,
                HrEmploymentContractStatus.READY, 0L
        ));

        HrProbationDtos.CompleteOnboardingResponse response = service.completeOnboarding(
                "candidate-1",
                new HrProbationDtos.CompleteOnboardingRequest(
                        0L,
                        "office-onboarding-1",
                        "NV001",
                        LocalDate.of(2026, 8, 15),
                        new HrApiDtos.EmploymentContractInput(
                                HrEmploymentContractType.INDEFINITE,
                                "002/HDLD/2026",
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 8, 15),
                                null
                        )
                ),
                actor()
        );

        ArgumentCaptor<HrApiDtos.CreateEmployeeRequest> request =
                ArgumentCaptor.forClass(HrApiDtos.CreateEmployeeRequest.class);
        verify(managementService).createEmployeeForOnboarding(
                request.capture(), eq(HrWorkforceGroup.OFFICE), eq(HrOnboardingSource.PROBATION), any());
        assertThat(request.getValue().employment().hireDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(candidate.getStatus()).isEqualTo(HrProbationCandidateStatus.CONVERTED);
        assertThat(candidate.getConvertedEmployee()).isEqualTo(employee);
        assertThat(response.nextAction()).isEqualTo("CREATE_INCREASE");
    }

    @Test
    void officeOnboardingRejectsIdempotencyReplayWithDifferentContractPayload() {
        HrProbationCandidate candidate = candidate(HrProbationCandidateStatus.CONVERTED);
        HrEmployee employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmployeeCode("NV001");
        HrEmploymentContract replay = new HrEmploymentContract();
        replay.setId("contract-1");
        replay.setEmployee(employee);
        replay.setSourceProbationCandidate(candidate);
        HrApiDtos.EmploymentContractInput contractInput = new HrApiDtos.EmploymentContractInput(
                HrEmploymentContractType.INDEFINITE,
                "002/HDLD/2026",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 15),
                null
        );

        when(employmentContractService.findByIdempotencyKey("office-onboarding-1"))
                .thenReturn(Optional.of(replay));
        when(employmentContractService.matchesInput(replay, contractInput)).thenReturn(false);

        assertThatThrownBy(() -> service.completeOnboarding(
                "candidate-1",
                new HrProbationDtos.CompleteOnboardingRequest(
                        0L,
                        "office-onboarding-1",
                        "NV001",
                        LocalDate.of(2026, 8, 15),
                        contractInput
                ),
                actor()
        ))
                .isInstanceOf(HrApiException.class)
                .satisfies(error -> assertThat(((HrApiException) error).code())
                        .isEqualTo("ONBOARDING_IDEMPOTENCY_CONFLICT"));
        verify(managementService, never()).createEmployeeForOnboarding(any(), any(), any(), any());
    }

    private static HrProbationCandidate candidate(HrProbationCandidateStatus status) {
        HrProbationCandidate candidate = new HrProbationCandidate();
        candidate.setId("candidate-1");
        candidate.setCandidateCode("TV001");
        candidate.setFullName("Nguyễn Văn Phòng");
        candidate.setGender(HrEmployeeGender.MALE);
        candidate.setProbationEndDate(LocalDate.of(2026, 8, 14));
        candidate.setStatus(status);
        candidate.setRowVersion(0L);
        return candidate;
    }

    private static HrImportActor actor() {
        return new HrImportActor("USER:manager-1", "HR Manager", "MANAGER");
    }
}
