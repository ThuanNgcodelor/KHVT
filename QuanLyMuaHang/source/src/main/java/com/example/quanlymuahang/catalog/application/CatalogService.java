package com.example.quanlymuahang.catalog.application;

import com.example.quanlymuahang.domain.material.Material;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.domain.supplier.Supplier;
import com.example.quanlymuahang.repository.MaterialRepository;
import com.example.quanlymuahang.repository.SupplierRepository;
import com.example.quanlymuahang.service.TextNormalizer;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {
    private final MaterialRepository materials;
    private final SupplierRepository suppliers;
    private final AuditRecorder audit;

    public CatalogService(MaterialRepository materials, SupplierRepository suppliers, AuditRecorder audit) {
        this.materials = materials; this.suppliers = suppliers; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MaterialView> searchMaterials(String query) {
        String normalized = TextNormalizer.normalize(query);
        return materials.findTop50ByActiveTrueAndNormalizedNameContainingOrderByNameAsc(normalized).stream().map(MaterialView::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<MaterialView> materialPage(String query, Boolean active, MaterialCategory category, Pageable pageable) {
        return materials.search(TextNormalizer.normalize(query), active, category, pageable).map(MaterialView::from);
    }

    @Transactional(readOnly = true)
    public MaterialView getMaterial(long id) {
        return MaterialView.from(materials.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy vật tư")));
    }

    @Transactional
    public MaterialView createMaterial(MaterialCommand command, long actorId) {
        String code = clean(command.code());
        if (code != null && materials.existsByCodeIgnoreCase(code)) throw ApiException.conflict("MATERIAL_CODE_EXISTS", "Mã vật tư đã tồn tại");
        Material entity = new Material(command.name().trim(), TextNormalizer.normalize(command.name()));
        entity.update(code, command.name().trim(), TextNormalizer.normalize(command.name()), command.category(), clean(command.defaultUnit()), true);
        MaterialView view = MaterialView.from(materials.save(entity));
        audit.record(actorId, "MATERIAL_CREATED", "MATERIAL", view.id(), view);
        return view;
    }

    @Transactional
    public MaterialView updateMaterial(long id, MaterialCommand command, long actorId) {
        Material entity = materials.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy vật tư"));
        String code = clean(command.code());
        if (code != null && materials.existsByCodeIgnoreCase(code) && !code.equalsIgnoreCase(entity.getCode()))
            throw ApiException.conflict("MATERIAL_CODE_EXISTS", "Mã vật tư đã tồn tại");
        entity.update(code, command.name().trim(), TextNormalizer.normalize(command.name()), command.category(), clean(command.defaultUnit()), command.active());
        MaterialView view = MaterialView.from(entity);
        audit.record(actorId, "MATERIAL_UPDATED", "MATERIAL", id, view);
        return view;
    }

    @Transactional(readOnly = true)
    public List<SupplierView> searchSuppliers(String query) {
        String normalized = TextNormalizer.normalize(query);
        return suppliers.findTop50ByActiveTrueAndNormalizedNameContainingOrderByNameAsc(normalized).stream().map(SupplierView::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<SupplierView> supplierPage(String query, Boolean active, Pageable pageable) {
        return suppliers.search(TextNormalizer.normalize(query), active, pageable).map(SupplierView::from);
    }

    @Transactional(readOnly = true)
    public SupplierView getSupplier(long id) {
        return SupplierView.from(suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp")));
    }

    @Transactional
    public SupplierView createSupplier(SupplierCommand command, long actorId) {
        String code = clean(command.code());
        if (code != null && suppliers.existsByCodeIgnoreCase(code)) throw ApiException.conflict("SUPPLIER_CODE_EXISTS", "Mã nhà cung cấp đã tồn tại");
        Supplier entity = new Supplier(command.name().trim(), TextNormalizer.normalize(command.name()));
        entity.update(code, command.name().trim(), TextNormalizer.normalize(command.name()), clean(command.address()), clean(command.taxCode()), clean(command.phone()), clean(command.email()), true);
        SupplierView view = SupplierView.from(suppliers.save(entity));
        audit.record(actorId, "SUPPLIER_CREATED", "SUPPLIER", view.id(), view);
        return view;
    }

    @Transactional
    public SupplierView updateSupplier(long id, SupplierCommand command, long actorId) {
        Supplier entity = suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
        String code = clean(command.code());
        if (code != null && suppliers.existsByCodeIgnoreCase(code) && !code.equalsIgnoreCase(entity.getCode()))
            throw ApiException.conflict("SUPPLIER_CODE_EXISTS", "Mã nhà cung cấp đã tồn tại");
        entity.update(code, command.name().trim(), TextNormalizer.normalize(command.name()), clean(command.address()), clean(command.taxCode()), clean(command.phone()), clean(command.email()), command.active());
        SupplierView view = SupplierView.from(entity);
        audit.record(actorId, "SUPPLIER_UPDATED", "SUPPLIER", id, view);
        return view;
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record MaterialCommand(String code, String name, MaterialCategory category, String defaultUnit, boolean active) {}
    public record SupplierCommand(String code, String name, String address, String taxCode, String phone, String email, boolean active) {}
    public record MaterialView(Long id, String code, String name, MaterialCategory category, String defaultUnit, boolean active) {
        static MaterialView from(Material entity) { return new MaterialView(entity.getId(), entity.getCode(), entity.getName(), entity.getCategory(), entity.getDefaultUnit(), entity.isActive()); }
    }
    public record SupplierView(Long id, String code, String name, String address, String taxCode, String phone, String email, boolean active) {
        static SupplierView from(Supplier entity) { return new SupplierView(entity.getId(), entity.getCode(), entity.getName(), entity.getAddress(), entity.getTaxCode(), entity.getPhone(), entity.getEmail(), entity.isActive()); }
    }
}
