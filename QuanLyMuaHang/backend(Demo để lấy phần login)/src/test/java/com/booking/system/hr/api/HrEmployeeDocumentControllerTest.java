package com.booking.system.hr.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.hr.api.dto.HrEmployeeDocumentDtos;
import com.booking.system.hr.enums.HrDocumentCategory;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.service.HrEmployeeDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrEmployeeDocumentControllerTest {

    @Mock
    private HrEmployeeDocumentService documentService;

    private HrEmployeeDocumentController controller;
    private User manager;

    @BeforeEach
    void setUp() {
        controller = new HrEmployeeDocumentController(documentService, new HrActorResolver());
        manager = new User();
        manager.setId("manager-123");
        manager.setEmail("manager@example.test");
        manager.setFullName("HR Lead");
        manager.setRole(RoleEnum.MANAGER);
        manager.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void getDocumentsReturnsSuccessList() {
        HrEmployeeDocumentDtos.DocumentSummary doc = new HrEmployeeDocumentDtos.DocumentSummary(
                "doc-1",
                "emp-1",
                HrDocumentCategory.CITIZEN_ID,
                "CCCD gắn chip",
                "cccd.pdf",
                "application/pdf",
                1024L,
                "sha256",
                "0123456789",
                LocalDate.of(2021, 5, 10),
                LocalDate.of(2031, 5, 10),
                "Cục CSQLHC về TTXH",
                null,
                LocalDateTime.now(),
                "USER:manager-123",
                LocalDateTime.now(),
                "USER:manager-123",
                0L
        );

        when(documentService.getDocuments("emp-1", null)).thenReturn(List.of(doc));

        ResponseEntity<ApiResponse<List<HrEmployeeDocumentDtos.DocumentSummary>>> response =
                controller.getDocuments("emp-1", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).documentName()).isEqualTo("CCCD gắn chip");
    }

    @Test
    void uploadDocumentExtractsActorFromPrincipal() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cccd.pdf",
                "application/pdf",
                "%PDF-1.4\ncontent".getBytes(StandardCharsets.UTF_8)
        );

        controller.uploadDocument(
                manager,
                "emp-1",
                file,
                HrDocumentCategory.CITIZEN_ID,
                "CCCD gắn chip",
                "0123456789",
                LocalDate.of(2021, 5, 10),
                LocalDate.of(2031, 5, 10),
                "Cục CSQLHC",
                "Ghi chú"
        );

        ArgumentCaptor<HrImportActor> actorCaptor = ArgumentCaptor.forClass(HrImportActor.class);
        ArgumentCaptor<HrEmployeeDocumentDtos.UploadRequest> requestCaptor =
                ArgumentCaptor.forClass(HrEmployeeDocumentDtos.UploadRequest.class);

        verify(documentService).uploadDocument(
                eq("emp-1"),
                requestCaptor.capture(),
                eq(file),
                actorCaptor.capture()
        );

        assertThat(actorCaptor.getValue().subject()).isEqualTo("USER:manager-123");
        assertThat(actorCaptor.getValue().displayName()).isEqualTo("HR Lead");
        assertThat(requestCaptor.getValue().documentName()).isEqualTo("CCCD gắn chip");
        assertThat(requestCaptor.getValue().documentCategory()).isEqualTo(HrDocumentCategory.CITIZEN_ID);
    }

    @Test
    void viewDocumentInlineSetsInlineContentDispositionHeader() {
        byte[] pdfBytes = "%PDF-1.4\nview-test".getBytes(StandardCharsets.UTF_8);
        when(documentService.getDocumentFile("doc-1")).thenReturn(
                new HrEmployeeDocumentDtos.DocumentFile("doc-1", "file-preview.pdf", "application/pdf", pdfBytes)
        );

        ResponseEntity<byte[]> response = controller.viewDocumentInline("doc-1");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("inline")
                .contains("file-preview.pdf");
        assertThat(response.getBody()).isEqualTo(pdfBytes);
    }

    @Test
    void downloadDocumentSetsAttachmentContentDispositionHeader() {
        byte[] pdfBytes = "%PDF-1.4\ndownload-test".getBytes(StandardCharsets.UTF_8);
        when(documentService.getDocumentFile("doc-1")).thenReturn(
                new HrEmployeeDocumentDtos.DocumentFile("doc-1", "file-download.pdf", "application/pdf", pdfBytes)
        );

        ResponseEntity<byte[]> response = controller.downloadDocument("doc-1");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("file-download.pdf");
        assertThat(response.getBody()).isEqualTo(pdfBytes);
    }

    @Test
    void uploadDocumentsBatchUsesPrincipalActor() {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "doc1.pdf", "application/pdf", "%PDF-1.4\n1".getBytes(StandardCharsets.UTF_8)
        );

        controller.uploadDocumentsBatch(
                manager,
                "emp-1",
                List.of(file1),
                HrDocumentCategory.DEGREE_CERTIFICATE
        );

        ArgumentCaptor<HrImportActor> actorCaptor = ArgumentCaptor.forClass(HrImportActor.class);
        verify(documentService).uploadDocumentsBatch(
                eq("emp-1"),
                eq(List.of(file1)),
                eq(HrDocumentCategory.DEGREE_CERTIFICATE),
                actorCaptor.capture()
        );
        assertThat(actorCaptor.getValue().subject()).isEqualTo("USER:manager-123");
    }
}
