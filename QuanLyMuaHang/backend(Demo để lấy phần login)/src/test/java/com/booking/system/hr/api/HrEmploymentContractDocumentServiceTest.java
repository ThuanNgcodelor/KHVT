package com.booking.system.hr.api;

import com.booking.system.hr.entity.HrDepartment;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeContact;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import com.booking.system.hr.entity.HrEmployeeIdentity;
import com.booking.system.hr.entity.HrEmploymentContract;
import com.booking.system.hr.entity.HrEmploymentContractDocument;
import com.booking.system.hr.entity.HrPosition;
import com.booking.system.hr.enums.HrEmployeeGender;
import com.booking.system.hr.enums.HrEmploymentContractStatus;
import com.booking.system.hr.enums.HrEmploymentContractType;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmploymentContractDocumentRepository;
import com.booking.system.hr.repository.HrEmploymentContractRepository;
import com.booking.system.hr.service.HrEmploymentContractDocumentService;
import com.booking.system.hr.service.HrEmploymentContractTemplateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrEmploymentContractDocumentServiceTest {

    @Mock private HrEmploymentContractRepository contractRepository;
    @Mock private HrEmploymentContractDocumentRepository documentRepository;
    @Mock private HrAuditEventRepository auditRepository;

    private HrEmploymentContractDocumentService service;

    @BeforeEach
    void setUp() {
        service = new HrEmploymentContractDocumentService(
                contractRepository,
                documentRepository,
                mock(HrEmploymentContractTemplateProvider.class, CALLS_REAL_METHODS),
                auditRepository,
                new HrImportJsonCodec()
        );
    }

    @Test
    void generatesOfficeDocumentFromSanitizedTemplateAndStoresImmutableSnapshot() throws IOException {
        HrEmploymentContract contract = contract(
                HrWorkforceGroup.OFFICE,
                HrEmploymentContractType.FIXED_TERM_12_MONTHS
        );
        when(contractRepository.findDocumentSourceByIdForUpdate("contract-1"))
                .thenReturn(Optional.of(contract));
        when(documentRepository.save(any(HrEmploymentContractDocument.class))).thenAnswer(invocation -> {
            HrEmploymentContractDocument document = invocation.getArgument(0);
            document.setId("document-1");
            return document;
        });

        var summary = service.generate("contract-1", actor());

        ArgumentCaptor<HrEmploymentContractDocument> captor =
                ArgumentCaptor.forClass(HrEmploymentContractDocument.class);
        verify(documentRepository).save(captor.capture());
        HrEmploymentContractDocument saved = captor.getValue();
        String xml = documentXml(saved.getGeneratedDocx());

        assertThat(summary.id()).isEqualTo("document-1");
        assertThat(summary.workforceGroup()).isEqualTo(HrWorkforceGroup.OFFICE);
        assertThat(summary.templateFileName()).isEqualTo("employment-contract-office-template.docx");
        assertThat(saved.getTemplateSha256()).hasSize(64);
        assertThat(saved.getGeneratedFileSha256()).hasSize(64);
        assertThat(saved.getSnapshotPayload()).contains("Nguyễn Văn Hợp Đồng");
        assertThat(xml)
                .contains("Nguyễn Văn Hợp Đồng")
                .contains("092206000001")
                .contains("Xác định thời hạn 12 tháng")
                .contains("Từ ngày: 15/08/2026 đến 15/08/2027")
                .contains("12.500.000 đồng")
                .doesNotContain("{{");
    }

    @Test
    void generatesGeneralLaborIndefiniteDocumentAndCanDownloadStoredBytes() throws IOException {
        HrEmploymentContract contract = contract(
                HrWorkforceGroup.GENERAL_LABOR,
                HrEmploymentContractType.INDEFINITE
        );
        when(contractRepository.findDocumentSourceByIdForUpdate("contract-1"))
                .thenReturn(Optional.of(contract));
        when(documentRepository.save(any(HrEmploymentContractDocument.class))).thenAnswer(invocation -> {
            HrEmploymentContractDocument document = invocation.getArgument(0);
            document.setId("document-2");
            return document;
        });

        var summary = service.generate("contract-1", actor());
        ArgumentCaptor<HrEmploymentContractDocument> captor =
                ArgumentCaptor.forClass(HrEmploymentContractDocument.class);
        verify(documentRepository).save(captor.capture());
        HrEmploymentContractDocument saved = captor.getValue();
        when(documentRepository.findDetailById("document-2")).thenReturn(Optional.of(saved));

        var file = service.download("document-2");
        String xml = documentXml(file.bytes());

        assertThat(summary.templateFileName())
                .isEqualTo("employment-contract-general-labor-template.docx");
        assertThat(xml)
                .contains("Không xác định thời hạn")
                .contains("Từ ngày: 15/08/2026.")
                .doesNotContain("đến 15/08/2027")
                .doesNotContain("{{");
        assertThat(file.fileName()).endsWith(".docx");
        assertThat(file.bytes()).isEqualTo(saved.getGeneratedDocx());
    }

    @Test
    void refusesToGenerateAnOfficialDocumentWhenRequiredEmployeeDataIsMissing() {
        HrEmploymentContract contract = contract(
                HrWorkforceGroup.GENERAL_LABOR,
                HrEmploymentContractType.FIXED_TERM_12_MONTHS
        );
        contract.getEmployee().setDateOfBirth(null);
        contract.getEmployee().getIdentity().setIssuedPlace(null);
        when(contractRepository.findDocumentSourceByIdForUpdate("contract-1"))
                .thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.generate("contract-1", actor()))
                .isInstanceOf(HrApiException.class)
                .satisfies(error -> {
                    HrApiException apiError = (HrApiException) error;
                    assertThat(apiError.code()).isEqualTo("EMPLOYMENT_CONTRACT_DOCUMENT_DATA_INCOMPLETE");
                    assertThat(apiError.getMessage()).contains("ngày sinh", "nơi cấp CCCD");
                });
        verify(documentRepository, never()).save(any());
    }

    private static HrEmploymentContract contract(
            HrWorkforceGroup workforceGroup,
            HrEmploymentContractType contractType
    ) {
        HrEmployee employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmployeeCode(workforceGroup == HrWorkforceGroup.OFFICE ? "VP001" : "LD001");
        employee.setFullName("Nguyễn Văn Hợp Đồng");
        employee.setGender(HrEmployeeGender.MALE);
        employee.setDateOfBirth(LocalDate.of(2000, 2, 14));
        employee.setBirthPlaceCurrent("Cần Thơ");
        employee.setWorkforceGroup(workforceGroup);

        HrDepartment department = new HrDepartment();
        department.setId("department-1");
        department.setName(workforceGroup == HrWorkforceGroup.OFFICE ? "Phòng Kinh doanh" : "Xí nghiệp");
        HrPosition position = new HrPosition();
        position.setId("position-1");
        position.setName(workforceGroup == HrWorkforceGroup.OFFICE ? "Nhân viên văn phòng" : "Công nhân sản xuất");

        HrEmployeeEmployment employment = new HrEmployeeEmployment();
        employment.setEmployee(employee);
        employment.setEmployeeId(employee.getId());
        employment.setDepartment(department);
        employment.setPosition(position);
        employment.setJobDescription("Thực hiện công việc theo phân công");
        employment.setBaseSalary(new BigDecimal("12500000"));
        employment.setAllowance(new BigDecimal("500000"));
        employee.setEmployment(employment);

        HrEmployeeIdentity identity = new HrEmployeeIdentity();
        identity.setEmployee(employee);
        identity.setEmployeeId(employee.getId());
        identity.setCitizenIdentityNumber("092206000001");
        identity.setIssuedDate(LocalDate.of(2021, 8, 13));
        identity.setIssuedPlace("Bộ Công an");
        employee.setIdentity(identity);

        HrEmployeeContact contact = new HrEmployeeContact();
        contact.setEmployee(employee);
        contact.setEmployeeId(employee.getId());
        contact.setPermanentAddress("Khu vực 1, phường Thới An Đông, Cần Thơ");
        employee.setContact(contact);

        HrEmploymentContract contract = new HrEmploymentContract();
        contract.setId("contract-1");
        contract.setEmployee(employee);
        contract.setContractNumber("001/HĐLĐ-PBHC/2026");
        contract.setSignDate(LocalDate.of(2026, 8, 10));
        contract.setEffectiveFrom(LocalDate.of(2026, 8, 15));
        contract.setContractType(contractType);
        contract.setEffectiveUntil(contractType == HrEmploymentContractType.FIXED_TERM_12_MONTHS
                ? LocalDate.of(2027, 8, 15)
                : null);
        contract.setStatus(HrEmploymentContractStatus.READY);
        return contract;
    }

    private static String documentXml(byte[] docx) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("Generated DOCX is missing word/document.xml");
    }

    private static HrImportActor actor() {
        return new HrImportActor("USER:manager-1", "HR Manager", "MANAGER");
    }
}
