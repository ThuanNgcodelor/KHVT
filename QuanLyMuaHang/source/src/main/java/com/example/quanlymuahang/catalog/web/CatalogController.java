package com.example.quanlymuahang.catalog.web;

import com.example.quanlymuahang.catalog.application.CatalogService;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }

    @GetMapping("/materials")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_READ')")
    public List<CatalogService.MaterialView> materials(@RequestParam(defaultValue = "") String q) { return service.searchMaterials(q); }

    @PostMapping("/materials")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.MaterialView createMaterial(@Valid @RequestBody MaterialRequest request, Authentication auth) { return service.createMaterial(request.command(), actor(auth)); }

    @PutMapping("/materials/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.MaterialView updateMaterial(@PathVariable long id, @Valid @RequestBody MaterialRequest request, Authentication auth) { return service.updateMaterial(id, request.command(), actor(auth)); }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_READ')")
    public List<CatalogService.SupplierView> suppliers(@RequestParam(defaultValue = "") String q) { return service.searchSuppliers(q); }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.SupplierView createSupplier(@Valid @RequestBody SupplierRequest request, Authentication auth) { return service.createSupplier(request.command(), actor(auth)); }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.SupplierView updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierRequest request, Authentication auth) { return service.updateSupplier(id, request.command(), actor(auth)); }

    private static long actor(Authentication authentication) { return ((AccountPrincipal) authentication.getPrincipal()).id(); }

    public record MaterialRequest(String code, @NotBlank @Size(max = 500) String name, @NotNull MaterialCategory category,
                                  String defaultUnit, boolean active) {
        CatalogService.MaterialCommand command() { return new CatalogService.MaterialCommand(code, name, category, defaultUnit, active); }
    }
    public record SupplierRequest(String code, @NotBlank @Size(max = 500) String name, String address,
                                  String taxCode, String phone, String email, boolean active) {
        CatalogService.SupplierCommand command() { return new CatalogService.SupplierCommand(code, name, address, taxCode, phone, email, active); }
    }
}
