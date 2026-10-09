package com.example.quanlymuahang.catalog.web;

import com.example.quanlymuahang.catalog.application.CatalogService;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

    @GetMapping("/materials/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_READ')")
    public Page<CatalogService.MaterialView> materialPage(@RequestParam(defaultValue = "") String q,
                                                         @RequestParam(required = false) Boolean active,
                                                         @RequestParam(required = false) MaterialCategory category,
                                                         @PageableDefault(size = 25) Pageable pageable) {
        return service.materialPage(q, active, category, pageable);
    }

    @GetMapping("/materials/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_READ')")
    public CatalogService.MaterialView material(@PathVariable long id) { return service.getMaterial(id); }

    @PostMapping("/materials")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.MaterialView createMaterial(@Valid @RequestBody MaterialRequest request, Authentication auth) { return service.createMaterial(request.command(), actor(auth)); }

    @PutMapping("/materials/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.MaterialView updateMaterial(@PathVariable long id, @Valid @RequestBody MaterialRequest request, Authentication auth) { return service.updateMaterial(id, request.command(), actor(auth)); }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_READ')")
    public List<CatalogService.SupplierView> suppliers(@RequestParam(defaultValue = "") String q) { return service.searchSuppliers(q); }

    @GetMapping("/suppliers/page")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_READ')")
    public Page<CatalogService.SupplierView> supplierPage(@RequestParam(defaultValue = "") String q,
                                                         @RequestParam(required = false) Boolean active,
                                                         @PageableDefault(size = 25) Pageable pageable) {
        return service.supplierPage(q, active, pageable);
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_READ')")
    public CatalogService.SupplierView supplier(@PathVariable long id) { return service.getSupplier(id); }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.SupplierView createSupplier(@Valid @RequestBody SupplierRequest request, Authentication auth) { return service.createSupplier(request.command(), actor(auth)); }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.SupplierView updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierRequest request, Authentication auth) { return service.updateSupplier(id, request.command(), actor(auth)); }

    private static long actor(Authentication authentication) { return ((AccountPrincipal) authentication.getPrincipal()).id(); }

    public record MaterialRequest(@Size(max = 80) String code, @NotBlank @Size(max = 500) String name, @NotNull MaterialCategory category,
                                  @Size(max = 100) String defaultUnit, boolean active) {
        CatalogService.MaterialCommand command() { return new CatalogService.MaterialCommand(code, name, category, defaultUnit, active); }
    }
    public record SupplierRequest(@Size(max = 50) String code, @NotBlank @Size(max = 500) String name, @Size(max = 1000) String address,
                                  @Size(max = 50) String taxCode, @Size(max = 50) String phone, @Size(max = 320) String email, boolean active) {
        CatalogService.SupplierCommand command() { return new CatalogService.SupplierCommand(code, name, address, taxCode, phone, email, active); }
    }
}
