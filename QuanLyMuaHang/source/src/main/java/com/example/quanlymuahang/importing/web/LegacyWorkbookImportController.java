package com.example.quanlymuahang.importing.web;

import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import com.example.quanlymuahang.importing.application.LegacyWorkbookImportService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports/legacy")
@PreAuthorize("hasAuthority('*') or hasAuthority('IMPORT_LEGACY')")
public class LegacyWorkbookImportController {
    private final LegacyWorkbookImportService imports;
    public LegacyWorkbookImportController(LegacyWorkbookImportService imports) { this.imports = imports; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LegacyWorkbookImportService.PreviewResult preview(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return imports.preview(file, actor(authentication));
    }

    @PostMapping("/{batchId}/commit")
    public LegacyWorkbookImportService.CommitResult commit(@PathVariable long batchId, Authentication authentication) {
        return imports.commit(batchId, actor(authentication));
    }

    private static long actor(Authentication authentication) { return ((AccountPrincipal) authentication.getPrincipal()).id(); }
}
