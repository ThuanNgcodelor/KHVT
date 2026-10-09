package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrEmployeeDocumentDtos;
import com.booking.system.hr.entity.HrAuditEvent;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeDocument;
import com.booking.system.hr.enums.HrDocumentCategory;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeDocumentRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrEmployeeDocumentServiceTest {

    @Mock
    private HrEmployeeRepository employeeRepository;
    @Mock
    private HrEmployeeDocumentRepository documentRepository;
    @Mock
    private HrAuditEventRepository auditRepository;

    private HrEmployeeDocumentService service;
    private final HrImportActor actor = new HrImportActor("manager@example.test", "Manager", "MANAGER");

    @BeforeEach
    void setUp() {
        service = new HrEmployeeDocumentService(
                employeeRepository,
                documentRepository,
                auditRepository,
                new HrImportJsonCodec()
        );
    }

    @Test
    void uploadDocumentSucceedsWithValidPdf() {
        HrEmployee employee = new HrEmployee();
        employee.setId("emp-1");
        employee.setEmployeeCode("A001");
        employee.setFullName("Nguyen Van A");

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(documentRepository.save(any())).thenAnswer(invocation -> {
            HrEmployeeDocument doc = invocation.getArgument(0);
            doc.setId("doc-1");
            return doc;
        });

        byte[] validPdfBytes = "%PDF-1.4\nTest PDF content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "BangDaiHoc.pdf",
                "application/pdf",
                validPdfBytes
        );

        HrEmployeeDocumentDtos.UploadRequest request = new HrEmployeeDocumentDtos.UploadRequest(
                HrDocumentCategory.DEGREE_CERTIFICATE,
                "Bằng Đại học Bách Khoa",
                "BK-2020-001",
                LocalDate.of(2020, 6, 30),
                null,
                "Đại học Bách Khoa",
                "Bằng tốt nghiệp loại giỏi"
        );

        HrEmployeeDocumentDtos.DocumentSummary result = service.uploadDocument(
                "emp-1",
                request,
                file,
                actor
        );

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("doc-1");
        assertThat(result.documentName()).isEqualTo("Bằng Đại học Bách Khoa");
        assertThat(result.documentCategory()).isEqualTo(HrDocumentCategory.DEGREE_CERTIFICATE);
        assertThat(result.fileType()).isEqualTo("application/pdf");
        assertThat(result.documentNumber()).isEqualTo("BK-2020-001");

        ArgumentCaptor<HrAuditEvent> auditCaptor = ArgumentCaptor.forClass(HrAuditEvent.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("HR_EMPLOYEE_DOCUMENT_UPLOADED");
    }

    @Test
    void uploadDocumentRejectsUnsupportedFileType() {
        HrEmployee employee = new HrEmployee();
        employee.setId("emp-1");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));

        byte[] fakeBytes = "NOT A SUPPORTED FILE".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                fakeBytes
        );

        HrEmployeeDocumentDtos.UploadRequest request = new HrEmployeeDocumentDtos.UploadRequest(
                HrDocumentCategory.OTHER,
                "Tài liệu test",
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.uploadDocument("emp-1", request, file, actor))
                .isInstanceOf(HrApiException.class)
                .hasMessageContaining("Định dạng file không được hỗ trợ");
    }

    @Test
    void uploadDocumentSucceedsWithWordDocxFile() {
        HrEmployee employee = new HrEmployee();
        employee.setId("emp-1");
        employee.setEmployeeCode("A001");
        employee.setFullName("Nguyen Van A");

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(documentRepository.save(any())).thenAnswer(invocation -> {
            HrEmployeeDocument doc = invocation.getArgument(0);
            doc.setId("doc-docx-1");
            return doc;
        });

        byte[] docxBytes = "PK\u0003\u0004FakeWordDocx".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "HopDongLaoDong.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        HrEmployeeDocumentDtos.UploadRequest request = new HrEmployeeDocumentDtos.UploadRequest(
                HrDocumentCategory.LABOR_CONTRACT,
                "Hợp đồng lao động chính thức",
                "01/HDLD",
                LocalDate.of(2026, 1, 1),
                null,
                "CFC",
                "Hợp đồng Word có chữ ký"
        );

        HrEmployeeDocumentDtos.DocumentSummary result = service.uploadDocument(
                "emp-1",
                request,
                file,
                actor
        );

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("doc-docx-1");
        assertThat(result.fileName()).isEqualTo("HopDongLaoDong.docx");
        assertThat(result.fileType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void getDocumentFileReturnsBytesForInlineOrDownload() {
        HrEmployee employee = new HrEmployee();
        employee.setId("emp-1");

        byte[] pdfBytes = "%PDF-1.4\nSample".getBytes(StandardCharsets.UTF_8);
        HrEmployeeDocument document = new HrEmployeeDocument();
        document.setId("doc-1");
        document.setEmployee(employee);
        document.setDocumentName("CCCD");
        document.setFileName("cccd.pdf");
        document.setFileType("application/pdf");
        document.setFileData(pdfBytes);

        when(documentRepository.findDetailById("doc-1")).thenReturn(Optional.of(document));

        HrEmployeeDocumentDtos.DocumentFile result = service.getDocumentFile("doc-1");
        assertThat(result.fileName()).isEqualTo("cccd.pdf");
        assertThat(result.fileType()).isEqualTo("application/pdf");
        assertThat(result.bytes()).isEqualTo(pdfBytes);
    }

    @Test
    void updateDocumentDetectsConflictOnStaleVersion() {
        HrEmployee employee = new HrEmployee();
        employee.setId("emp-1");

        HrEmployeeDocument document = new HrEmployeeDocument();
        document.setId("doc-1");
        document.setEmployee(employee);
        document.setRowVersion(2L);

        when(documentRepository.findDetailById("doc-1")).thenReturn(Optional.of(document));

        HrEmployeeDocumentDtos.UpdateRequest request = new HrEmployeeDocumentDtos.UpdateRequest(
                HrDocumentCategory.CITIZEN_ID,
                "CCCD mới",
                "123456",
                null,
                null,
                null,
                null,
                1L // Stale version
        );

        assertThatThrownBy(() -> service.updateDocument("doc-1", request, actor))
                .isInstanceOf(HrApiException.class)
                .hasMessageContaining("Hồ sơ đã được chỉnh sửa ở nơi khác");
    }

    @Test
    void deleteDocumentRemovesRecordAndRecordsAudit() {
        HrEmployee employee = new HrEmployee();
        employee.setId("emp-1");

        HrEmployeeDocument document = new HrEmployeeDocument();
        document.setId("doc-1");
        document.setEmployee(employee);
        document.setDocumentCategory(HrDocumentCategory.CITIZEN_ID);
        document.setDocumentName("CCCD gắn chip");
        document.setFileName("cccd.pdf");
        document.setFileSizeBytes(1024L);
        document.setRowVersion(0L);

        when(documentRepository.findDetailById("doc-1")).thenReturn(Optional.of(document));

        service.deleteDocument("doc-1", 0L, actor);

        verify(documentRepository).delete(document);
        ArgumentCaptor<HrAuditEvent> auditCaptor = ArgumentCaptor.forClass(HrAuditEvent.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("HR_EMPLOYEE_DOCUMENT_DELETED");
    }

    @Test
    void uploadDocumentsBatchSucceedsWithMultiplePdfs() {
        HrEmployee employee = new HrEmployee();
        employee.setId("emp-1");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(employee));
        when(documentRepository.save(any())).thenAnswer(invocation -> {
            HrEmployeeDocument doc = invocation.getArgument(0);
            doc.setId("batch-doc-" + System.nanoTime());
            return doc;
        });

        MockMultipartFile file1 = new MockMultipartFile(
                "files", "Bang_Dai_Hoc.pdf", "application/pdf", "%PDF-1.4\nfile1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "Chung_Chi_IELTS.pdf", "application/pdf", "%PDF-1.4\nfile2".getBytes(StandardCharsets.UTF_8)
        );

        List<HrEmployeeDocumentDtos.DocumentSummary> results = service.uploadDocumentsBatch(
                "emp-1",
                List.of(file1, file2),
                HrDocumentCategory.DEGREE_CERTIFICATE,
                actor
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).documentName()).isEqualTo("Bang Dai Hoc");
        assertThat(results.get(0).documentCategory()).isEqualTo(HrDocumentCategory.DEGREE_CERTIFICATE);
        assertThat(results.get(1).documentName()).isEqualTo("Chung Chi IELTS");
    }
}
