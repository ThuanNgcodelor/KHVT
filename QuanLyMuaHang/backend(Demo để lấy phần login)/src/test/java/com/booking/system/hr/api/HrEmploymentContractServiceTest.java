package com.booking.system.hr.api;

import com.booking.system.hr.dto.HrApiDtos;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import com.booking.system.hr.entity.HrEmploymentContract;
import com.booking.system.hr.enums.HrEmploymentContractStatus;
import com.booking.system.hr.enums.HrEmploymentContractType;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeEmploymentRepository;
import com.booking.system.hr.repository.HrEmploymentContractRepository;
import com.booking.system.hr.service.HrEmploymentContractService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrEmploymentContractServiceTest {

    @Mock private HrEmploymentContractRepository contractRepository;
    @Mock private HrEmployeeEmploymentRepository employmentRepository;
    @Mock private HrAuditEventRepository auditRepository;
    @Mock private EntityManager entityManager;

    private HrEmploymentContractService service;
    private HrEmployee employee;
    private HrEmployeeEmployment employment;

    @BeforeEach
    void setUp() {
        service = new HrEmploymentContractService(
                contractRepository,
                employmentRepository,
                auditRepository,
                new HrImportJsonCodec(),
                entityManager
        );
        employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmployeeCode("NV001");
        employee.setOnboardingPolicyVersion((short) 2);
        employment = new HrEmployeeEmployment();
        employment.setEmployee(employee);
        employment.setEmployeeId(employee.getId());
        employee.setEmployment(employment);
    }

    @Test
    void createsReadyContractAndUpdatesEmployeeProjection() {
        when(contractRepository.findByIdempotencyKey("onboarding-1")).thenReturn(Optional.empty());
        when(contractRepository.existsByContractNumber("001/HDLD/2026")).thenReturn(false);
        when(contractRepository.findCurrentForUpdate(anyString(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(contractRepository.save(any(HrEmploymentContract.class))).thenAnswer(invocation -> {
            HrEmploymentContract saved = invocation.getArgument(0);
            saved.setId("contract-1");
            return saved;
        });

        HrEmploymentContract contract = service.createReadyContract(
                employee,
                null,
                fixedContract(),
                "onboarding-1",
                actor()
        );

        assertThat(contract.getStatus()).isEqualTo(HrEmploymentContractStatus.READY);
        assertThat(contract.getContractType()).isEqualTo(HrEmploymentContractType.FIXED_TERM_12_MONTHS);
        assertThat(contract.getEffectiveUntil()).isEqualTo(LocalDate.of(2027, 8, 15));
        assertThat(employment.getContractNumber()).isEqualTo("001/HDLD/2026");
        assertThat(employment.getContractTypeLabel()).contains("12 tháng");
        verify(employmentRepository).save(employment);
        verify(entityManager).flush();
    }

    @Test
    void rejectsInvalidTwelveMonthEndBeforeWriting() {
        HrApiDtos.EmploymentContractInput invalid = new HrApiDtos.EmploymentContractInput(
                HrEmploymentContractType.FIXED_TERM_12_MONTHS,
                "002/HDLD/2026",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2027, 8, 14)
        );

        assertThatThrownBy(() -> service.createReadyContract(employee, null, invalid, "invalid-end", actor()))
                .isInstanceOf(HrApiException.class)
                .satisfies(error -> assertThat(((HrApiException) error).code())
                        .isEqualTo("EMPLOYMENT_CONTRACT_12_MONTH_END_INVALID"));
        verify(contractRepository, never()).save(any());
    }

    @Test
    void policyTwoRequiresMatchingReadyContractAndActivationMarksItEffective() {
        HrEmploymentContract contract = new HrEmploymentContract();
        contract.setId("contract-1");
        contract.setEmployee(employee);
        contract.setStatus(HrEmploymentContractStatus.READY);
        contract.setEffectiveFrom(LocalDate.of(2026, 8, 15));
        when(contractRepository.findCurrentForUpdate(anyString(), any(), any(Pageable.class)))
                .thenReturn(List.of(contract));
        when(contractRepository.save(contract)).thenReturn(contract);

        HrEmploymentContract ready = service.requireReadyForIncrease(employee, LocalDate.of(2026, 8, 15));
        service.markEffective(ready, actor());

        assertThat(contract.getStatus()).isEqualTo(HrEmploymentContractStatus.EFFECTIVE);
        assertThat(contract.getActivatedAt()).isNotNull();
        assertThat(contract.getActivatedByActor()).isEqualTo(actor().subject());
        verify(contractRepository).save(contract);
    }

    @Test
    void legacyPolicyRemainsExemptFromContractGate() {
        employee.setOnboardingPolicyVersion((short) 1);

        assertThat(service.requireReadyForIncrease(employee, LocalDate.of(2026, 8, 15))).isNull();
        verify(contractRepository, never()).findCurrentForUpdate(anyString(), any(), any(Pageable.class));
    }

    private static HrApiDtos.EmploymentContractInput fixedContract() {
        return new HrApiDtos.EmploymentContractInput(
                HrEmploymentContractType.FIXED_TERM_12_MONTHS,
                "001/HDLD/2026",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2027, 8, 15)
        );
    }

    private static HrImportActor actor() {
        return new HrImportActor("USER:manager-1", "HR Manager", "MANAGER");
    }
}
