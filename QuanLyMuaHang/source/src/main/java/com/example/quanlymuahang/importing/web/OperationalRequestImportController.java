package com.example.quanlymuahang.importing.web;

import com.example.quanlymuahang.importing.application.OperationalRequestImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports/operational")
@PreAuthorize("hasAuthority('*') or hasAuthority('IMPORT_OPERATIONAL')")
public class OperationalRequestImportController {
    private final OperationalRequestImportService imports;
    public OperationalRequestImportController(OperationalRequestImportService imports) { this.imports = imports; }
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OperationalRequestImportService.DraftPreview preview(@RequestPart("file") MultipartFile file) { return imports.preview(file); }
    @PostMapping("/paste")
    public OperationalRequestImportService.DraftPreview paste(@Valid @RequestBody PasteRequest body) { return imports.paste(body.content()); }
    public record PasteRequest(@NotBlank @Size(max = 200_000) String content) {}
}
