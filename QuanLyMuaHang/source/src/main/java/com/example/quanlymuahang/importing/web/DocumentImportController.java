package com.example.quanlymuahang.importing.web;

import com.example.quanlymuahang.importing.application.*;
import com.example.quanlymuahang.importing.application.DocumentImportService.*;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/imports/documents")
@PreAuthorize("hasAuthority('*') or hasAuthority('IMPORT_OPERATIONAL')")
public class DocumentImportController {
    private final DocumentImportService service;
    public DocumentImportController(DocumentImportService service) { this.service = service; }
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView preview(@RequestPart("file") MultipartFile file, @RequestParam String kind, @RequestParam(required = false) String sheet, Authentication auth) { return service.preview(file, kind, sheet, actor(auth)); }
    @GetMapping public List<DocumentView> list() { return service.list(); }
    @GetMapping("/{id}") public DocumentView get(@PathVariable long id) { return service.get(id); }
    @PostMapping("/{id}/confirm") public DocumentView confirm(@PathVariable long id, @RequestBody Confirmation body, Authentication auth) { return service.confirm(id, body, actor(auth)); }
    @GetMapping("/{id}/source") public ResponseEntity<byte[]> source(@PathVariable long id) { return file(service.source(id), MediaType.APPLICATION_OCTET_STREAM); }
    @PostMapping("/{id}/lookup")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('IMPORT_OPERATIONAL') and hasAuthority('PRICE_READ'))")
    public DocumentHistoryLookup.Lookup lookup(@PathVariable long id) { return service.lookup(id); }
    @PostMapping("/{id}/inquiries")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('IMPORT_OPERATIONAL') and hasAuthority('PRICE_READ'))")
    public DocumentView inquiries(@PathVariable long id, @RequestBody InquiryBody body, Authentication auth) { return service.saveInquiries(id, body, actor(auth)); }
    @PostMapping("/{id}/inquiries.xlsx")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('IMPORT_OPERATIONAL') and hasAuthority('PRICE_READ'))")
    public ResponseEntity<byte[]> xlsx(@PathVariable long id, @RequestBody InquiryBody body, Authentication auth) { return file(service.inquiriesXlsx(id, body, actor(auth)), MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")); }
    @GetMapping("/{id}/comparison")
    @PreAuthorize("hasAuthority('*') or (hasAuthority('IMPORT_OPERATIONAL') and hasAuthority('PRICE_READ'))")
    public DocumentHistoryLookup.Comparison comparison(@PathVariable long id) { return service.comparison(id); }
    private static long actor(Authentication a) { return ((AccountPrincipal) a.getPrincipal()).id(); }
    private static ResponseEntity<byte[]> file(SourceFile f, MediaType type) { return ResponseEntity.ok().contentType(type)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString()).body(f.content()); }
}
