package com.example.quanlymuahang.catalog.web;

import com.example.quanlymuahang.catalog.application.CatalogService;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public CatalogService.MaterialView createMaterial(@Valid @RequestBody MaterialRequest request) { return service.createMaterial(request.command()); }

    @PutMapping("/materials/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.MaterialView updateMaterial(@PathVariable long id, @Valid @RequestBody MaterialRequest request) { return service.updateMaterial(id, request.command()); }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_READ')")
    public List<CatalogService.SupplierView> suppliers(@RequestParam(defaultValue = "") String q) { return service.searchSuppliers(q); }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.SupplierView createSupplier(@Valid @RequestBody SupplierRequest request) { return service.createSupplier(request.command()); }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('*') or hasAuthority('CATALOG_MANAGE')")
    public CatalogService.SupplierView updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierRequest request) { return service.updateSupplier(id, request.command()); }

    public record MaterialRequest(String code, @NotBlank @Size(max = 500) String name, @NotNull MaterialCategory category,
                                  String defaultUnit, boolean active) {
        CatalogService.MaterialCommand command() { return new CatalogService.MaterialCommand(code, name, category, defaultUnit, active); }
    }
    public record SupplierRequest(String code, @NotBlank @Size(max = 500) String name, String address,
                                  String taxCode, String phone, String email, boolean active) {
        CatalogService.SupplierCommand command() { return new CatalogService.SupplierCommand(code, name, address, taxCode, phone, email, active); }
    }
}
