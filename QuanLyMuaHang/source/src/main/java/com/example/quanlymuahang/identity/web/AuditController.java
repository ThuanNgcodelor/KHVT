package com.example.quanlymuahang.identity.web;

import com.example.quanlymuahang.sharedkernel.application.AuditQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasAuthority('*') or hasAuthority('AUDIT_READ')")
public class AuditController {
    private final AuditQueryService audit;
    public AuditController(AuditQueryService audit) { this.audit = audit; }
    @GetMapping
    public AuditQueryService.AuditPage search(@RequestParam(required = false) String action,
                                               @RequestParam(required = false) String entityType,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        return audit.search(action, entityType, page, size);
    }
}
